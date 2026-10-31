"""Consultas reales: las ventas anuladas no representan demanda ni ingresos."""
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
        conexion.execute(text("CREATE TEMP TABLE ventas (id BIGINT, fecha TIMESTAMP, anulada BOOLEAN)"))
        conexion.execute(text("CREATE TEMP TABLE detalle_venta (venta_id BIGINT, producto_id BIGINT, cantidad INTEGER, precio_unitario NUMERIC)"))
        ayer = datetime.now().replace(hour=12, minute=0, second=0, microsecond=0) - timedelta(days=1)
        conexion.execute(text("INSERT INTO ventas VALUES (1,:fecha,false),(2,:fecha,true)"), {"fecha": ayer})
        conexion.execute(text("INSERT INTO detalle_venta VALUES (1,1,2,10),(2,1,9,10)"))
        monkeypatch.setattr(db, "engine", SimpleNamespace(connect=lambda: nullcontext(conexion)))
        yield ayer.date()
        conexion.rollback()


def test_resumen_excluye_ventas_anuladas(ventas_reales):
    assert db.resumen_ventas(30) == {"unidades": 2, "ingresos": 20.0}


def test_demanda_excluye_ventas_anuladas(ventas_reales):
    serie = db.ventas_diarias(30, producto_id=1)
    assert serie.loc[ventas_reales, 1] == 2
    assert serie[1].sum() == 2
