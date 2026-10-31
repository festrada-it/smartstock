from datetime import date, timedelta

import numpy as np
from fastapi import APIRouter, Depends, FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

from app import config, db
from app.auth import usuario_actual
from app.prediccion import ALTA, MEDIA, analizar_producto, proyectar_demanda
from app.schemas import Prediccion, ProyeccionStock, PuntoSerie, Resumen, TendenciaVentas

app = FastAPI(title="SmartStock - Analytics Service", version="0.2.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=config.CORS_ORIGENES,
    allow_methods=["GET"],
    allow_headers=["*"],
)

api = APIRouter(prefix="/api", dependencies=[Depends(usuario_actual)])

_ORDEN_CRITICIDAD = {ALTA: 0, MEDIA: 1}


@app.get("/health")
def health():
    return {"servicio": "analytics-service", "estado": "OK"}


@api.get("/predicciones", response_model=list[Prediccion])
def predicciones():
    """Predicción de quiebre para todos los productos activos, ordenada por urgencia."""
    return _predecir_todos()


@api.get("/alertas", response_model=list[Prediccion])
def alertas():
    """Productos con riesgo de quiebre ALTO o MEDIO, con su lote de compra sugerido."""
    return [p for p in _predecir_todos() if p["criticidad"] in (ALTA, MEDIA)]


@api.get("/predicciones/{producto_id}", response_model=Prediccion)
def prediccion_producto(producto_id: int):
    producto = _buscar_producto(producto_id)
    return _predecir(producto, _ventas(producto_id), db.obtener_parametros())


@api.get("/predicciones/{producto_id}/proyeccion", response_model=ProyeccionStock)
def proyeccion_stock(producto_id: int,
                     dias_historico: int = Query(30, ge=1, le=90),
                     dias_futuro: int = Query(15, ge=1, le=60)):
    """Stock histórico reconstruido + stock proyectado (para el gráfico del Monitor Predictivo)."""
    producto = _buscar_producto(producto_id)
    ventas = _ventas(producto_id)
    historico = db.stock_historico(producto_id, producto["stock_actual"], dias_historico)

    demanda = proyectar_demanda(ventas, dias_futuro)
    consumo = np.cumsum(demanda) if demanda is not None else np.zeros(dias_futuro)
    stock_futuro = np.clip(producto["stock_actual"] - consumo, 0, None)

    return {
        "producto": _predecir(producto, ventas, db.obtener_parametros()),
        "historico": _serie(historico.index, historico.values),
        "proyeccion": _serie(_proximos_dias(dias_futuro), stock_futuro),
    }


@api.get("/predicciones/{producto_id}/tendencia", response_model=TendenciaVentas)
def tendencia_ventas(producto_id: int,
                     dias_historico: int = Query(30, ge=1, le=90),
                     dias_futuro: int = Query(15, ge=1, le=60)):
    """Ventas diarias recientes vs. pronóstico de demanda."""
    _buscar_producto(producto_id)
    ventas = _ventas(producto_id)
    demanda = proyectar_demanda(ventas, dias_futuro)
    fechas_historico = [date.today() - timedelta(days=i) for i in range(dias_historico, 0, -1)]
    return {
        "producto_id": producto_id,
        "ventas": _serie(fechas_historico, ventas[-dias_historico:]),
        "pronostico": _serie(_proximos_dias(dias_futuro), demanda if demanda is not None else np.zeros(dias_futuro)),
    }


@api.get("/resumen", response_model=Resumen)
def resumen():
    """Métricas globales para las tarjetas del dashboard."""
    predicciones = _predecir_todos()
    productos = db.obtener_productos()
    ventas = db.resumen_ventas(30)
    return {
        "productos_activos": len(productos),
        "valor_total_stock": round(sum(p["stock_actual"] * p["costo"] for p in productos), 2),
        "alertas_alta": sum(1 for p in predicciones if p["criticidad"] == ALTA),
        "alertas_media": sum(1 for p in predicciones if p["criticidad"] == MEDIA),
        "unidades_vendidas_30_dias": ventas["unidades"],
        "ingresos_30_dias": ventas["ingresos"],
    }


app.include_router(api)


def _predecir_todos() -> list[dict]:
    parametros = db.obtener_parametros()
    ventas = db.ventas_diarias(config.DIAS_HISTORIAL)
    resultado = []
    for producto in db.obtener_productos():
        serie = ventas[producto["id"]].tolist() if producto["id"] in ventas.columns else [0.0] * len(ventas)
        resultado.append(_predecir(producto, serie, parametros))
    return sorted(resultado, key=lambda p: (_ORDEN_CRITICIDAD.get(p["criticidad"], 2),
                                            p["dias_hasta_quiebre"] is None, p["dias_hasta_quiebre"] or 0))


def _predecir(producto: dict, ventas: list[float], parametros: dict) -> dict:
    estimacion = analizar_producto(ventas, producto["stock_actual"], producto["stock_minimo"], parametros)
    return {**producto, **estimacion}


def _ventas(producto_id: int) -> list[float]:
    df = db.ventas_diarias(config.DIAS_HISTORIAL, producto_id)
    return df[producto_id].tolist() if producto_id in df.columns else [0.0] * len(df)


def _buscar_producto(producto_id: int) -> dict:
    producto = db.obtener_producto(producto_id)
    if producto is None:
        raise HTTPException(status_code=404, detail="Producto no encontrado")
    return producto


def _proximos_dias(dias: int) -> list[date]:
    return [date.today() + timedelta(days=i) for i in range(1, dias + 1)]


def _serie(fechas, valores) -> list[dict]:
    return [{"fecha": f, "valor": round(float(v), 2)} for f, v in zip(fechas, valores)]
