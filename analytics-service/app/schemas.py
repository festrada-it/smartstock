"""Modelos de respuesta (definen el contrato JSON que consume el front-end)."""
from datetime import date

from pydantic import BaseModel


class Prediccion(BaseModel):
    id: int
    sku: str
    nombre: str
    stock_actual: int
    stock_minimo: int
    demanda_diaria_estimada: float | None
    dias_hasta_quiebre: int | None
    fecha_quiebre_estimada: date | None
    criticidad: str
    lote_sugerido: int
    lead_time_dias: int
    margen_dias: int
    plazo_efectivo_dias: int
    proveedor_id: int | None
    proveedor: str | None
    punto_reposicion: float | None
    fecha_reposicion: date | None
    comprar_ahora: bool | None


class PuntoSerie(BaseModel):
    fecha: date
    valor: float


class ProyeccionStock(BaseModel):
    producto: Prediccion
    historico: list[PuntoSerie]
    proyeccion: list[PuntoSerie]


class TendenciaVentas(BaseModel):
    producto_id: int
    ventas: list[PuntoSerie]
    pronostico: list[PuntoSerie]


class Vencimiento(BaseModel):
    lote_id: int
    producto_id: int
    sku: str
    producto: str
    marca: str | None
    grupo: str | None
    cantidad_disponible: int
    fecha_vencimiento: date
    dias_restantes: int
    valor_en_riesgo: float
    criticidad: str
    accion_sugerida: str
    dias_alerta_grupo: int


class Resumen(BaseModel):
    productos_activos: int
    valor_total_stock: float
    alertas_alta: int
    alertas_media: int
    unidades_vendidas_30_dias: int
    ingresos_30_dias: float
