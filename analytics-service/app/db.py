"""Acceso a datos (solo lectura). Las series se devuelven como pandas, con un valor por día."""
from datetime import date, timedelta

import pandas as pd
from sqlalchemy import create_engine, text

from app import config

engine = create_engine(config.DATABASE_URL, pool_pre_ping=True)

_COLUMNAS_PRODUCTO = "id, sku, nombre, stock_actual, stock_minimo, costo, precio"


def obtener_productos() -> list[dict]:
    with engine.connect() as conn:
        filas = conn.execute(text(f"SELECT {_COLUMNAS_PRODUCTO} FROM productos WHERE activo ORDER BY id"))
        return [_producto(fila) for fila in filas]


def obtener_producto(producto_id: int) -> dict | None:
    with engine.connect() as conn:
        fila = conn.execute(
            text(f"SELECT {_COLUMNAS_PRODUCTO} FROM productos WHERE id = :id AND activo"), {"id": producto_id}
        ).first()
        return _producto(fila) if fila else None


def obtener_parametros() -> dict[str, float]:
    with engine.connect() as conn:
        filas = conn.execute(text("SELECT clave, valor FROM parametros"))
        guardados = {fila.clave: float(fila.valor) for fila in filas}
    return {**config.PARAMETROS_POR_DEFECTO, **guardados}


def ventas_diarias(dias: int, producto_id: int | None = None) -> pd.DataFrame:
    """Unidades vendidas por día (filas) y producto (columnas) en los últimos `dias` días completos.
    Los días sin ventas valen 0. No incluye el día de hoy porque todavía está en curso."""
    hoy = date.today()
    desde = hoy - timedelta(days=dias)
    params = {"desde": desde, "hoy": hoy}
    filtro = ""
    if producto_id is not None:
        filtro = "AND d.producto_id = :producto_id"
        params["producto_id"] = producto_id
    consulta = text(f"""
        SELECT DATE(v.fecha) AS dia, d.producto_id, SUM(d.cantidad) AS unidades
        FROM detalle_venta d
        JOIN ventas v ON v.id = d.venta_id
        WHERE v.fecha >= :desde AND v.fecha < :hoy {filtro}
        GROUP BY DATE(v.fecha), d.producto_id
    """)
    with engine.connect() as conn:
        df = pd.read_sql(consulta, conn, params=params)

    indice = pd.date_range(desde, hoy - timedelta(days=1), freq="D").date
    if df.empty:
        return pd.DataFrame(index=indice)
    return df.pivot_table(index="dia", columns="producto_id", values="unidades", aggfunc="sum").reindex(indice, fill_value=0).fillna(0)


def stock_historico(producto_id: int, stock_actual: int, dias: int) -> pd.Series:
    """Reconstruye el stock al cierre de cada uno de los últimos `dias` días (incluido hoy)
    a partir del stock actual y los movimientos registrados."""
    hoy = date.today()
    desde = hoy - timedelta(days=dias - 1)
    with engine.connect() as conn:
        df = pd.read_sql(
            text("""
                SELECT DATE(fecha) AS dia, SUM(cantidad) AS neto
                FROM movimientos_stock
                WHERE producto_id = :producto_id AND fecha >= :desde
                GROUP BY DATE(fecha)
            """),
            conn,
            params={"producto_id": producto_id, "desde": desde},
        )
    indice = pd.date_range(desde, hoy, freq="D").date
    neto = df.set_index("dia")["neto"].reindex(indice, fill_value=0).astype(float) if not df.empty else pd.Series(0.0, index=indice)
    # stock al cierre del día d = stock actual - movimientos posteriores a d
    posteriores = neto[::-1].cumsum()[::-1].shift(-1, fill_value=0)
    return (stock_actual - posteriores).clip(lower=0)


def resumen_ventas(dias: int) -> dict:
    with engine.connect() as conn:
        fila = conn.execute(
            text("""
                SELECT COALESCE(SUM(d.cantidad), 0) AS unidades,
                       COALESCE(SUM(d.cantidad * d.precio_unitario), 0) AS ingresos
                FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id
                WHERE v.fecha >= :desde
            """),
            {"desde": date.today() - timedelta(days=dias)},
        ).one()
    return {"unidades": int(fila.unidades), "ingresos": float(fila.ingresos)}


def _producto(fila) -> dict:
    datos = dict(fila._mapping)
    datos["costo"] = float(datos["costo"])
    datos["precio"] = float(datos["precio"])
    return datos
