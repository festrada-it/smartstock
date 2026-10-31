"""Lógica predictiva: proyección de demanda y estimación de quiebre de stock.

Este módulo no depende de la base de datos, así se puede testear de forma aislada.
"""
from datetime import date, timedelta

import numpy as np
from sklearn.linear_model import LinearRegression


def estimar_quiebre(ventas_diarias: list[float], stock_actual: int, horizonte_dias: int = 60) -> dict:
    """Ajusta una regresión lineal sobre las ventas diarias históricas y proyecta
    en cuántos días se agotaría el stock actual.

    ventas_diarias: unidades vendidas por día, ordenadas de la más antigua a la más reciente.
    """
    if stock_actual <= 0:
        return _resultado(demanda_diaria=None, dias=0)
    if len(ventas_diarias) < 2:
        return _resultado(demanda_diaria=None, dias=None)

    x = np.arange(len(ventas_diarias)).reshape(-1, 1)
    y = np.array(ventas_diarias, dtype=float)
    modelo = LinearRegression().fit(x, y)

    futuros = np.arange(len(ventas_diarias), len(ventas_diarias) + horizonte_dias).reshape(-1, 1)
    demanda_proyectada = np.clip(modelo.predict(futuros), 0, None)
    acumulado = np.cumsum(demanda_proyectada)

    agotado = acumulado >= stock_actual
    dias = int(np.argmax(agotado)) + 1 if agotado.any() else None
    return _resultado(demanda_diaria=round(float(demanda_proyectada[0]), 2), dias=dias)


def _resultado(demanda_diaria: float | None, dias: int | None) -> dict:
    return {
        "demanda_diaria_estimada": demanda_diaria,
        "dias_hasta_quiebre": dias,
        "fecha_quiebre_estimada": (date.today() + timedelta(days=dias)).isoformat() if dias is not None else None,
    }
