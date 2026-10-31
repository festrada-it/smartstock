"""Cálculo de reposición por proveedor, sin acceso a datos ni al reloj."""
import math
from datetime import date, timedelta


def plazo_reposicion(lead_time_dias: int | None, clasificacion: int | None,
                     calidad: str | None, dias_reposicion: int,
                     margen_dias_proveedor_bajo: int, clasificacion_proveedor_baja: int) -> int:
    if dias_reposicion < 0 or margen_dias_proveedor_bajo < 0:
        raise ValueError("El plazo y el margen de reposición no pueden ser negativos")
    if not 1 <= clasificacion_proveedor_baja <= 5:
        raise ValueError("El límite de clasificación debe estar entre 1 y 5")
    if lead_time_dias is None:
        return dias_reposicion
    if lead_time_dias < 0:
        raise ValueError("El lead time del proveedor no puede ser negativo")
    if clasificacion is None or not 1 <= clasificacion <= 5:
        raise ValueError("La clasificación del proveedor debe estar entre 1 y 5")
    if calidad not in ("BUENA", "REGULAR", "MALA"):
        raise ValueError("La calidad del proveedor debe ser BUENA, REGULAR o MALA")
    margen = margen_dias_proveedor_bajo if clasificacion <= clasificacion_proveedor_baja or calidad == "MALA" else 0
    return lead_time_dias + margen


def analizar_reposicion(demanda_proyectada: list[float] | None, stock_actual: int,
                        stock_minimo: int, lead_time_dias: int, plazo_efectivo: int,
                        hoy: date) -> dict:
    if stock_actual < 0 or stock_minimo < 0:
        raise ValueError("El stock actual y el mínimo no pueden ser negativos")
    if lead_time_dias < 0 or plazo_efectivo < lead_time_dias:
        raise ValueError("El plazo efectivo debe ser no negativo y al menos igual al lead time")
    resultado = {
        "lead_time_dias": lead_time_dias,
        "punto_reposicion": None,
        "fecha_reposicion": None,
        "comprar_ahora": None,
    }
    if demanda_proyectada is None:
        return resultado
    if not demanda_proyectada:
        raise ValueError("La demanda proyectada no puede estar vacía")
    if any(not math.isfinite(demanda) or demanda < 0 for demanda in demanda_proyectada):
        raise ValueError("La demanda proyectada debe ser finita y no negativa")

    punto = demanda_proyectada[0] * plazo_efectivo + stock_minimo
    if not math.isfinite(punto):
        raise ValueError("El punto de reposición debe ser finito")
    comprar_ahora = stock_actual <= punto
    fecha = hoy if comprar_ahora else None
    if not comprar_ahora:
        stock = float(stock_actual)
        for dia, demanda in enumerate(demanda_proyectada, start=1):
            stock = max(0.0, stock - demanda)
            if stock <= punto:
                fecha = hoy + timedelta(days=dia)
                break
    return {
        "lead_time_dias": lead_time_dias,
        "punto_reposicion": punto,
        "fecha_reposicion": fecha,
        "comprar_ahora": comprar_ahora,
    }
