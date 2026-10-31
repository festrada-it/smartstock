"""Lógica predictiva: proyección de demanda, estimación de quiebre, criticidad y lote sugerido.

Este módulo no depende de la base de datos, así se puede testear de forma aislada.
"""
import math
from datetime import date, timedelta

import numpy as np
from sklearn.linear_model import LinearRegression

HORIZONTE_DIAS = 60

ALTA = "ALTA"
MEDIA = "MEDIA"
BAJA = "BAJA"


def proyectar_demanda(ventas_diarias: list[float], dias: int) -> np.ndarray | None:
    """Ajusta una regresión lineal (tendencia) sobre las ventas diarias históricas
    y devuelve la demanda estimada para cada uno de los próximos `dias` días.
    Devuelve None si no hay historial suficiente para entrenar."""
    if len(ventas_diarias) < 2:
        return None
    x = np.arange(len(ventas_diarias)).reshape(-1, 1)
    modelo = LinearRegression().fit(x, np.asarray(ventas_diarias, dtype=float))
    futuros = np.arange(len(ventas_diarias), len(ventas_diarias) + dias).reshape(-1, 1)
    return np.clip(modelo.predict(futuros), 0, None)


def dias_hasta_quiebre(demanda_proyectada: np.ndarray | None, stock_actual: int) -> int | None:
    """Cantidad de días hasta que la demanda acumulada consume el stock. None si no ocurre en el horizonte."""
    if stock_actual <= 0:
        return 0
    if demanda_proyectada is None:
        return None
    agotado = np.cumsum(demanda_proyectada) >= stock_actual
    return int(np.argmax(agotado)) + 1 if agotado.any() else None


def clasificar_criticidad(dias: int | None, stock_actual: int, stock_minimo: int,
                          umbral_alta: float, umbral_media: float) -> str:
    if dias is not None and dias <= umbral_alta:
        return ALTA
    if (dias is not None and dias <= umbral_media) or stock_actual <= stock_minimo:
        return MEDIA
    return BAJA


def lote_sugerido(demanda_proyectada: np.ndarray | None, stock_actual: int, stock_minimo: int,
                  dias_reposicion: int, dias_cobertura: int) -> int:
    """Unidades a comprar para cubrir la demanda mientras llega el pedido más el período de cobertura,
    manteniendo el stock mínimo de seguridad."""
    demanda = 0.0 if demanda_proyectada is None else float(demanda_proyectada[: dias_reposicion + dias_cobertura].sum())
    return max(0, math.ceil(demanda + stock_minimo - stock_actual))


def analizar_producto(ventas_diarias: list[float], stock_actual: int, stock_minimo: int, parametros: dict) -> dict:
    reposicion = int(parametros["dias_reposicion"])
    cobertura = int(parametros["dias_cobertura"])
    demanda = proyectar_demanda(ventas_diarias, max(HORIZONTE_DIAS, reposicion + cobertura))
    dias = dias_hasta_quiebre(demanda, stock_actual)
    return {
        "demanda_diaria_estimada": round(float(demanda[0]), 2) if demanda is not None else None,
        "dias_hasta_quiebre": dias,
        "fecha_quiebre_estimada": date.today() + timedelta(days=dias) if dias is not None else None,
        "criticidad": clasificar_criticidad(dias, stock_actual, stock_minimo,
                                            parametros["umbral_criticidad_alta"], parametros["umbral_criticidad_media"]),
        "lote_sugerido": lote_sugerido(demanda, stock_actual, stock_minimo, reposicion, cobertura),
    }
