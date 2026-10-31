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


class Resumen(BaseModel):
    productos_activos: int
    valor_total_stock: float
    alertas_alta: int
    alertas_media: int
    unidades_vendidas_30_dias: int
    ingresos_30_dias: float
