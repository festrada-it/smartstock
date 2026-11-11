"""Regla de rotación independiente de las consultas y los estados del pedido."""
import math


def analizar_rotacion(unidades_vendidas: int, stock_historico: list[float],
                      umbral_rotacion: float) -> dict:
    """Evalúa las ventas y el stock al cierre de los mismos 30 días."""
    if unidades_vendidas < 0:
        raise ValueError("Las unidades vendidas no pueden ser negativas")
    if not math.isfinite(umbral_rotacion) or umbral_rotacion < 0:
        raise ValueError("El umbral de rotación debe ser finito y no negativo")
    if len(stock_historico) != 30:
        raise ValueError("El historial de stock debe contener exactamente 30 días")
    if any(not math.isfinite(stock) or stock < 0 for stock in stock_historico):
        raise ValueError("El stock histórico debe ser finito y no negativo")

    stock_promedio = math.fsum(stock / 30 for stock in stock_historico)
    rotacion = unidades_vendidas / stock_promedio if stock_promedio > 0 else None
    if rotacion is None:
        estado = "SIN_STOCK"
        sugerencia = "Sin stock"
    elif rotacion >= umbral_rotacion:
        estado = "SANA"
        sugerencia = "Seguir reponiendo según la predicción"
    else:
        estado = "BAJA"
        sugerencia = "No recomprar / promocionar" if rotacion < umbral_rotacion / 2 else "Comprar menos"

    return {
        "unidades_vendidas": unidades_vendidas,
        "stock_promedio": stock_promedio,
        "rotacion": rotacion,
        "umbral_rotacion": umbral_rotacion,
        "estado": estado,
        "sugerencia": sugerencia,
    }
