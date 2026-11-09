"""Consultas reales: solo los pedidos CONFIRMADO, ENVIADO y ENTREGADO son demanda e ingresos."""
from contextlib import nullcontext
from datetime import datetime, timedelta
import os
from types import SimpleNamespace

import pytest
from sqlalchemy import text

from app import db


@pytest.fixture
def ventas_reales(monkeypatch):
    if os.environ.get("SMARTSTOCK_INTEGRACION") != "true":
        pytest.skip("Requiere PostgreSQL; CI habilita SMARTSTOCK_INTEGRACION=true")
    with db.engine.connect() as conexion:
        # Tablas temporales: no se modifica el historial de ninguna base existente.
        conexion.execute(text("CREATE TEMP TABLE ventas (id BIGINT, fecha TIMESTAMP, estado TEXT, total NUMERIC)"))
        conexion.execute(text("CREATE TEMP TABLE detalle_venta (venta_id BIGINT, producto_id BIGINT, cantidad INTEGER, precio_unitario NUMERIC)"))
        ayer = datetime.now().replace(hour=12, minute=0, second=0, microsecond=0) - timedelta(days=1)
        # La venta 1 tiene dos líneas y 10% de descuento: 20 de lista, 18 de total.
        conexion.execute(text("INSERT INTO ventas VALUES (1,:fecha,'ENTREGADO',18),(2,:fecha,'PENDIENTE',90),"
                              "(3,:fecha,'CANCELADO',50),(4,:fecha,'CONFIRMADO',10),(5,:fecha,'ENVIADO',30)"),
                         {"fecha": ayer})
        conexion.execute(text("INSERT INTO detalle_venta VALUES (1,1,1,10),(1,1,1,10),(2,1,9,10),"
                              "(3,1,5,10),(4,1,1,10),(5,2,3,10)"))
        monkeypatch.setattr(db, "engine", SimpleNamespace(connect=lambda: nullcontext(conexion)))
        yield ayer.date()
        conexion.rollback()


def test_resumen_cuenta_solo_pedidos_que_salieron_del_deposito(ventas_reales):
    # ENTREGADO 2 + CONFIRMADO 1 + ENVIADO 3 unidades; PENDIENTE y CANCELADO no suman.
    assert db.resumen_ventas(30)["unidades"] == 6


def test_resumen_suma_el_total_con_descuento_sin_repetirlo_por_linea(ventas_reales):
    assert db.resumen_ventas(30)["ingresos"] == 58.0  # 18 + 10 + 30, no 20 + 10 + 30


def test_demanda_excluye_pendientes_y_cancelados(ventas_reales):
    serie = db.ventas_diarias(30, producto_id=1)
    assert serie.loc[ventas_reales, 1] == 3  # 2 de la venta 1 y 1 de la venta 4
    assert serie[1].sum() == 3
