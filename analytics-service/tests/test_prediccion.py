from fastapi.testclient import TestClient

from app.main import app
from app.prediccion import estimar_quiebre


def test_health():
    respuesta = TestClient(app).get("/health")
    assert respuesta.status_code == 200
    assert respuesta.json()["estado"] == "OK"


def test_demanda_constante_estima_dias_hasta_quiebre():
    # 5 unidades por día con 48 en stock -> se agota al día 10
    resultado = estimar_quiebre([5.0] * 30, stock_actual=48)
    assert resultado["dias_hasta_quiebre"] == 10
    assert resultado["demanda_diaria_estimada"] == 5.0


def test_sin_stock_quiebre_inmediato():
    assert estimar_quiebre([3.0] * 10, stock_actual=0)["dias_hasta_quiebre"] == 0


def test_sin_ventas_no_hay_quiebre_en_el_horizonte():
    assert estimar_quiebre([0.0] * 30, stock_actual=10)["dias_hasta_quiebre"] is None


def test_historial_insuficiente():
    assert estimar_quiebre([4.0], stock_actual=10)["dias_hasta_quiebre"] is None
