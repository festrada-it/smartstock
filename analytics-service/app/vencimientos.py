"""Reglas de vencimiento por lote, independientes de la base de datos."""
from datetime import date
from decimal import Decimal


def analizar_vencimiento(fecha_vencimiento: date | None, cantidad_disponible: int,
                         costo_unitario: Decimal, dias_alerta_vencimiento: int,
                         hoy: date) -> dict | None:
    """Devuelve la alerta si el lote tiene stock y vence dentro de la ventana del grupo."""
    if cantidad_disponible < 0:
        raise ValueError("La cantidad disponible no puede ser negativa")
    if not costo_unitario.is_finite() or costo_unitario < 0:
        raise ValueError("El costo unitario debe ser finito y no negativo")
    if dias_alerta_vencimiento < 0:
        raise ValueError("Los dias de alerta de vencimiento no pueden ser negativos")

    if cantidad_disponible == 0 or fecha_vencimiento is None:
        return None

    dias_restantes = (fecha_vencimiento - hoy).days
    if dias_restantes > dias_alerta_vencimiento:
        return None

    if dias_restantes <= 7:
        criticidad = "ALTA"
    elif dias_restantes <= dias_alerta_vencimiento / 2:
        criticidad = "MEDIA"
    else:
        criticidad = "BAJA"

    return {
        "cantidad_disponible": cantidad_disponible,
        "fecha_vencimiento": fecha_vencimiento,
        "dias_restantes": dias_restantes,
        "valor_en_riesgo": cantidad_disponible * costo_unitario,
        "criticidad": criticidad,
        "accion_sugerida": "Retirar de la venta" if dias_restantes < 0 else "Vender primero",
    }


_ORDEN_CRITICIDAD = {"ALTA": 0, "MEDIA": 1, "BAJA": 2}


def listar_vencimientos(lotes: list[dict], hoy: date) -> list[dict]:
    """Alertas de todos los lotes que corresponden, con los datos del lote y su producto,
    de la más crítica a la menos crítica y, a igual criticidad, la que vence antes."""
    alertas = []
    for lote in lotes:
        alerta = analizar_vencimiento(lote["fecha_vencimiento"], lote["cantidad_disponible"],
                                      lote["costo_unitario"], lote["dias_alerta_vencimiento"], hoy)
        if alerta is None:
            continue
        alertas.append({
            "lote_id": lote["lote_id"], "producto_id": lote["producto_id"], "sku": lote["sku"],
            "producto": lote["producto"], "marca": lote["marca"], "grupo": lote["grupo"],
            **alerta, "dias_alerta_grupo": lote["dias_alerta_vencimiento"],
        })
    return sorted(alertas, key=lambda a: (_ORDEN_CRITICIDAD[a["criticidad"]], a["dias_restantes"], a["lote_id"]))
