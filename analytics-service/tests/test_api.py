import jwt
import pandas as pd
import pytest
from fastapi.testclient import TestClient

from app import config, db
from app.main import app

cliente = TestClient(app)

PRODUCTOS = [
    {"id": 1, "sku": "A", "nombre": "Mucho stock", "stock_actual": 500, "stock_minimo": 10, "costo": 100.0, "precio": 150.0},
    {"id": 2, "sku": "B", "nombre": "Poco stock", "stock_actual": 8, "stock_minimo": 10, "costo": 50.0, "precio": 80.0},
]


def _token(rol="ADMIN", secreto=config.JWT_SECRET):
    return jwt.encode({"sub": "admin@smartstock.com", "rol": rol}, secreto, algorithm="HS512")


@pytest.fixture
def db_falsa(monkeypatch):
    ventas = pd.DataFrame({1: [2.0] * 90, 2: [4.0] * 90})
    monkeypatch.setattr(db, "obtener_productos", lambda: PRODUCTOS)
    monkeypatch.setattr(db, "obtener_parametros", lambda: dict(config.PARAMETROS_POR_DEFECTO))
    monkeypatch.setattr(db, "ventas_diarias", lambda dias, producto_id=None: ventas)


def test_health_es_publico():
    respuesta = cliente.get("/health")
    assert respuesta.status_code == 200
    assert respuesta.json()["estado"] == "OK"


def test_sin_token_responde_401():
    assert cliente.get("/api/predicciones").status_code == 401


def test_token_con_otro_secreto_responde_401():
    headers = {"Authorization": f"Bearer {_token(secreto='otro-secreto-que-no-es-el-del-sistema-0123456789abcdef')}"}
    assert cliente.get("/api/predicciones", headers=headers).status_code == 401


def test_predicciones_ordenadas_por_criticidad(db_falsa):
    respuesta = cliente.get("/api/predicciones", headers={"Authorization": f"Bearer {_token()}"})

    assert respuesta.status_code == 200
    datos = respuesta.json()
    assert [p["id"] for p in datos] == [2, 1]
    assert datos[0]["criticidad"] == "ALTA"
    assert datos[0]["dias_hasta_quiebre"] == 2
    assert datos[1]["criticidad"] == "BAJA"


def test_alertas_solo_incluye_riesgo_alto_o_medio(db_falsa):
    respuesta = cliente.get("/api/alertas", headers={"Authorization": f"Bearer {_token('VENDEDOR')}"})

    assert respuesta.status_code == 200
    assert [p["id"] for p in respuesta.json()] == [2]
