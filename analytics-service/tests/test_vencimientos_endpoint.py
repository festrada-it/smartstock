"""GET /api/vencimientos: contrato HTTP y consulta de lotes."""
from contextlib import nullcontext
from datetime import date, timedelta
from decimal import Decimal
import os
from types import SimpleNamespace

import jwt
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text

from app import config, db
from app.main import app

cliente = TestClient(app)


def _encabezados(rol="VENDEDOR"):
    token = jwt.encode({"sub": "admin@smartstock.com", "rol": rol}, config.JWT_SECRET, algorithm="HS512")
    return {"Authorization": f"Bearer {token}"}


def _lote(lote_id, dias, **datos):
    base = {"lote_id": lote_id, "producto_id": 4, "sku": "HEL-001", "producto": "Helado 1L", "marca": "Frigor",
            "grupo": "Congelados", "cantidad_disponible": 40, "costo_unitario": Decimal("30.00"),
            "dias_alerta_vencimiento": 30, "fecha_vencimiento": date.today() + timedelta(days=dias)}
    return {**base, **datos}


def test_sin_token_responde_401():
    assert cliente.get("/api/vencimientos").status_code == 401


def test_devuelve_el_contrato_completo_ordenado_por_criticidad(monkeypatch):
    monkeypatch.setattr(db, "lotes_con_vencimiento", lambda: [_lote(1, 20, marca=None, grupo=None), _lote(2, 3)])

    respuesta = cliente.get("/api/vencimientos", headers=_encabezados())

    assert respuesta.status_code == 200
    datos = respuesta.json()
    assert [l["lote_id"] for l in datos] == [2, 1]
    assert datos[0] == {
        "lote_id": 2, "producto_id": 4, "sku": "HEL-001", "producto": "Helado 1L", "marca": "Frigor",
        "grupo": "Congelados", "cantidad_disponible": 40,
        "fecha_vencimiento": (date.today() + timedelta(days=3)).isoformat(), "dias_restantes": 3,
        "valor_en_riesgo": 1200.0, "criticidad": "ALTA", "accion_sugerida": "Vender primero",
        "dias_alerta_grupo": 30,
    }
    assert datos[1]["marca"] is None and datos[1]["grupo"] is None


def test_lote_vencido_se_retira_de_la_venta(monkeypatch):
    monkeypatch.setattr(db, "lotes_con_vencimiento", lambda: [_lote(1, -2)])

    lote = cliente.get("/api/vencimientos", headers=_encabezados()).json()[0]

    assert (lote["dias_restantes"], lote["criticidad"], lote["accion_sugerida"]) == (-2, "ALTA", "Retirar de la venta")


def test_sin_lotes_devuelve_lista_vacia(monkeypatch):
    monkeypatch.setattr(db, "lotes_con_vencimiento", lambda: [])

    assert cliente.get("/api/vencimientos", headers=_encabezados()).json() == []


@pytest.fixture
def lotes_reales(monkeypatch):
    if os.environ.get("SMARTSTOCK_INTEGRACION") != "true":
        pytest.skip("Requiere PostgreSQL; CI habilita SMARTSTOCK_INTEGRACION=true")
    with db.engine.connect() as conexion:
        # Tablas temporales: ocultan a las reales durante la prueba y no modifican ninguna base.
        conexion.execute(text("CREATE TEMP TABLE categorias (id BIGINT, nombre TEXT, dias_alerta_vencimiento INTEGER)"))
        conexion.execute(text("CREATE TEMP TABLE productos (id BIGINT, sku TEXT, nombre TEXT, marca TEXT, categoria_id BIGINT)"))
        conexion.execute(text("CREATE TEMP TABLE lotes (id BIGINT, producto_id BIGINT, cantidad_disponible INTEGER, "
                              "fecha_vencimiento DATE, costo_unitario NUMERIC)"))
        conexion.execute(text("INSERT INTO categorias VALUES (1,'Congelados',30)"))
        conexion.execute(text("INSERT INTO productos VALUES (1,'A','Con grupo','Marca',1),(2,'B','Sin grupo',NULL,NULL)"))
        conexion.execute(text("""
            INSERT INTO lotes VALUES
              (1,1,10,CURRENT_DATE + 30,5),   -- vence justo en el límite de la ventana: entra
              (2,1,10,CURRENT_DATE + 31,5),   -- un día fuera de la ventana
              (3,1,10,CURRENT_DATE - 4,5),    -- ya vencido: entra
              (4,1,0,CURRENT_DATE + 2,5),     -- sin disponible
              (5,1,10,NULL,5),                -- sin fecha de vencimiento
              (6,2,7,CURRENT_DATE + 60,2.5),  -- sin grupo: ventana de 60 días
              (7,2,7,CURRENT_DATE + 61,2.5)
        """))
        monkeypatch.setattr(db, "engine", SimpleNamespace(connect=lambda: nullcontext(conexion)))
        yield
        conexion.rollback()


def test_consulta_devuelve_solo_lotes_dentro_de_la_ventana_del_grupo(lotes_reales):
    lotes = {l["lote_id"]: l for l in db.lotes_con_vencimiento()}

    assert set(lotes) == {1, 3, 6}
    assert lotes[1]["dias_alerta_vencimiento"] == 30 and lotes[1]["grupo"] == "Congelados"
    assert lotes[6]["dias_alerta_vencimiento"] == config.DIAS_ALERTA_VENCIMIENTO_POR_DEFECTO
    assert lotes[6]["grupo"] is None and lotes[6]["marca"] is None
    assert lotes[3]["costo_unitario"] == Decimal("5")


def test_endpoint_completo_sobre_la_consulta(lotes_reales):
    respuesta = cliente.get("/api/vencimientos", headers=_encabezados())

    assert respuesta.status_code == 200
    assert [(l["lote_id"], l["criticidad"]) for l in respuesta.json()] == [(3, "ALTA"), (1, "BAJA"), (6, "BAJA")]
