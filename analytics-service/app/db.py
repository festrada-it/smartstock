"""Acceso a datos (solo lectura). Las series se devuelven como pandas, con un valor por día."""
from datetime import date, timedelta

import pandas as pd
from sqlalchemy import create_engine, text

from app import config

engine = create_engine(config.DATABASE_URL, pool_pre_ping=True)

# Solo los pedidos que salieron del depósito son demanda e ingreso: ni los PENDIENTES ni los CANCELADOS.
ESTADOS_QUE_VENDEN = ["CONFIRMADO", "ENVIADO", "ENTREGADO"]

_SELECT_PRODUCTO = """
    SELECT p.id, p.sku, p.nombre, p.stock_actual, p.stock_minimo, p.costo, p.precio,
           pr.id AS proveedor_id, pr.nombre AS proveedor,
           pr.lead_time_dias AS proveedor_lead_time_dias,
           pr.clasificacion AS proveedor_clasificacion, pr.calidad AS proveedor_calidad
    FROM productos p
    LEFT JOIN proveedores pr ON pr.id = p.proveedor_id AND pr.activo
"""


def obtener_productos() -> list[dict]:
    with engine.connect() as conn:
        filas = conn.execute(text(f"{_SELECT_PRODUCTO} WHERE p.activo ORDER BY p.id"))
        return [_producto(fila) for fila in filas]


def obtener_producto(producto_id: int) -> dict | None:
    with engine.connect() as conn:
        fila = conn.execute(
            text(f"{_SELECT_PRODUCTO} WHERE p.id = :id AND p.activo"), {"id": producto_id}
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
    params = {"desde": desde, "hoy": hoy, "estados": ESTADOS_QUE_VENDEN}
    filtro = ""
    if producto_id is not None:
        filtro = "AND d.producto_id = :producto_id"
        params["producto_id"] = producto_id
    consulta = text(f"""
        SELECT DATE(v.fecha) AS dia, d.producto_id, SUM(d.cantidad) AS unidades
        FROM detalle_venta d
        JOIN ventas v ON v.id = d.venta_id
        WHERE v.fecha >= :desde AND v.fecha < :hoy AND v.estado = ANY(:estados) {filtro}
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
    """Unidades e ingresos de los pedidos vendidos. El ingreso es el total del pedido, con su descuento."""
    parametros = {"desde": date.today() - timedelta(days=dias), "estados": ESTADOS_QUE_VENDEN}
    with engine.connect() as conn:
        # Dos consultas: unir con el detalle repetiría el total del pedido por cada línea.
        unidades = conn.execute(
            text("""
                SELECT COALESCE(SUM(d.cantidad), 0)
                FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id
                WHERE v.fecha >= :desde AND v.estado = ANY(:estados)
            """),
            parametros,
        ).scalar_one()
        ingresos = conn.execute(
            text("SELECT COALESCE(SUM(total), 0) FROM ventas WHERE fecha >= :desde AND estado = ANY(:estados)"),
            parametros,
        ).scalar_one()
    return {"unidades": int(unidades), "ingresos": float(ingresos)}


def lotes_con_vencimiento() -> list[dict]:
    """Lotes con disponible y fecha de vencimiento, con la ventana de alerta de su grupo (60 días sin grupo).
    Ya descarta los que vencen más allá de la ventana; la criticidad la decide app.vencimientos."""
    with engine.connect() as conn:
        filas = conn.execute(text("""
            SELECT * FROM (
                SELECT l.id AS lote_id, l.producto_id, p.sku, p.nombre AS producto, p.marca,
                       c.nombre AS grupo, l.cantidad_disponible, l.fecha_vencimiento, l.costo_unitario,
                       COALESCE(c.dias_alerta_vencimiento, :dias_por_defecto) AS dias_alerta_vencimiento
                FROM lotes l
                JOIN productos p ON p.id = l.producto_id
                LEFT JOIN categorias c ON c.id = p.categoria_id
                WHERE l.cantidad_disponible > 0 AND l.fecha_vencimiento IS NOT NULL
            ) lote
            WHERE fecha_vencimiento - CURRENT_DATE <= dias_alerta_vencimiento
            ORDER BY lote_id
        """), {"dias_por_defecto": config.DIAS_ALERTA_VENCIMIENTO_POR_DEFECTO})
        return [dict(fila._mapping) for fila in filas]


def lineas_fill_rate(desde: date, hasta: date) -> tuple[list[dict], list[dict]]:
    """Unidades vendidas y faltantes (ventas perdidas) con fecha en [desde, hasta), por cliente y producto.
    No filtra por grupo ni cliente: eso lo hace app.fill_rate, que también arma el período anterior."""
    parametros = {"desde": desde, "hasta": hasta}
    with engine.connect() as conn:
        vendidas = conn.execute(text("""
            SELECT v.cliente_id, COALESCE(c.nombre, 'Sin cliente') AS cliente, d.producto_id,
                   p.nombre AS producto, p.categoria_id AS grupo_id, SUM(d.cantidad) AS unidades
            FROM detalle_venta d
            JOIN ventas v ON v.id = d.venta_id
            JOIN productos p ON p.id = d.producto_id
            LEFT JOIN clientes c ON c.id = v.cliente_id
            WHERE v.estado = ANY(:estados) AND v.fecha >= :desde AND v.fecha < :hasta
            GROUP BY v.cliente_id, c.nombre, d.producto_id, p.nombre, p.categoria_id
        """), {**parametros, "estados": ESTADOS_QUE_VENDEN})
        vendidas = [{**fila._mapping, "unidades": int(fila.unidades)} for fila in vendidas]
        perdidas = conn.execute(text("""
            SELECT vp.cliente_id, COALESCE(c.nombre, 'Sin cliente') AS cliente, vp.producto_id,
                   p.nombre AS producto, p.categoria_id AS grupo_id,
                   SUM(vp.cantidad_pedida - vp.cantidad_disponible) AS faltante,
                   SUM((vp.cantidad_pedida - vp.cantidad_disponible) * vp.precio_unitario) AS monto
            FROM ventas_perdidas vp
            JOIN productos p ON p.id = vp.producto_id
            LEFT JOIN clientes c ON c.id = vp.cliente_id
            WHERE vp.fecha >= :desde AND vp.fecha < :hasta
            GROUP BY vp.cliente_id, c.nombre, vp.producto_id, p.nombre, p.categoria_id
        """), parametros)
        perdidas = [{**fila._mapping, "faltante": int(fila.faltante)} for fila in perdidas]
    return vendidas, perdidas


def _producto(fila) -> dict:
    datos = dict(fila._mapping)
    datos["costo"] = float(datos["costo"])
    datos["precio"] = float(datos["precio"])
    return datos
