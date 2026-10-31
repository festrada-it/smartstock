import os
from datetime import date, timedelta

from sqlalchemy import create_engine, text

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql+psycopg://smartstock:smartstock@localhost:5432/smartstock")

engine = create_engine(DATABASE_URL, pool_pre_ping=True)


def obtener_productos() -> list[dict]:
    with engine.connect() as conn:
        filas = conn.execute(text(
            "SELECT id, sku, nombre, stock_actual, stock_minimo FROM productos WHERE activo ORDER BY id"
        ))
        return [dict(fila._mapping) for fila in filas]


def obtener_producto(producto_id: int) -> dict | None:
    with engine.connect() as conn:
        fila = conn.execute(
            text("SELECT id, sku, nombre, stock_actual, stock_minimo FROM productos WHERE id = :id"),
            {"id": producto_id},
        ).first()
        return dict(fila._mapping) if fila else None


def ventas_diarias(producto_id: int, dias: int = 90) -> list[float]:
    """Unidades vendidas por día en los últimos `dias` días (los días sin ventas cuentan como 0)."""
    desde = date.today() - timedelta(days=dias)
    with engine.connect() as conn:
        filas = conn.execute(text("""
            SELECT DATE(v.fecha) AS dia, SUM(d.cantidad) AS unidades
            FROM detalle_venta d
            JOIN ventas v ON v.id = d.venta_id
            WHERE d.producto_id = :producto_id AND v.fecha >= :desde AND v.fecha < CURRENT_DATE
            GROUP BY DATE(v.fecha)
        """), {"producto_id": producto_id, "desde": desde})
        por_dia = {fila.dia: float(fila.unidades) for fila in filas}

    return [por_dia.get(desde + timedelta(days=i), 0.0) for i in range(dias)]
