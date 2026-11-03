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


PRODUCTOS_CON_PROVEEDOR = [
    {"id": 1, "sku": "B", "nombre": "Proveedor bueno", "stock_actual": 100, "stock_minimo": 30, "costo": 10.0, "precio": 15.0,
     "proveedor_id": 1, "proveedor": "Distribuidora del Sur", "proveedor_lead_time_dias": 7,
     "proveedor_clasificacion": 5, "proveedor_calidad": "BUENA"},
    {"id": 2, "sku": "M", "nombre": "Proveedor malo", "stock_actual": 100, "stock_minimo": 30, "costo": 10.0, "precio": 15.0,
     "proveedor_id": 2, "proveedor": "Alimentos Frescos Norte", "proveedor_lead_time_dias": 7,
     "proveedor_clasificacion": 1, "proveedor_calidad": "MALA"},
    {"id": 3, "sku": "S", "nombre": "Sin proveedor", "stock_actual": 100, "stock_minimo": 30, "costo": 10.0, "precio": 15.0,
     "proveedor_id": None, "proveedor": None, "proveedor_lead_time_dias": None,
     "proveedor_clasificacion": None, "proveedor_calidad": None},
    {"id": 4, "sku": "X", "nombre": "Proveedor defectuoso", "stock_actual": 100, "stock_minimo": 30, "costo": 10.0, "precio": 15.0,
     "proveedor_id": 4, "proveedor": "Datos inválidos", "proveedor_lead_time_dias": 7,
     "proveedor_clasificacion": 9, "proveedor_calidad": "BUENA"},
]


def _encabezados():
    return {"Authorization": f"Bearer {_token()}"}


@pytest.fixture
def db_falsa_proveedores(monkeypatch):
    ventas = pd.DataFrame({p["id"]: [5.0] * 90 for p in PRODUCTOS_CON_PROVEEDOR})
    monkeypatch.setattr(db, "obtener_productos", lambda: PRODUCTOS_CON_PROVEEDOR)
    monkeypatch.setattr(db, "obtener_producto", lambda id: next((p for p in PRODUCTOS_CON_PROVEEDOR if p["id"] == id), None))
    monkeypatch.setattr(db, "obtener_parametros", lambda: dict(config.PARAMETROS_POR_DEFECTO))
    monkeypatch.setattr(db, "ventas_diarias", lambda dias, producto_id=None: ventas)


def test_predicciones_suma_los_campos_de_reposicion_sin_quitar_ninguno(db_falsa_proveedores):
    respuesta = cliente.get("/api/predicciones", headers=_encabezados())

    assert respuesta.status_code == 200
    por_id = {p["id"]: p for p in respuesta.json()}
    for campo in ("id", "sku", "nombre", "stock_actual", "stock_minimo", "demanda_diaria_estimada",
                  "dias_hasta_quiebre", "fecha_quiebre_estimada", "criticidad", "lote_sugerido"):
        assert campo in por_id[1]
    bueno, malo = por_id[1], por_id[2]
    assert (bueno["lead_time_dias"], bueno["margen_dias"], bueno["plazo_efectivo_dias"]) == (7, 0, 7)
    assert (malo["lead_time_dias"], malo["margen_dias"], malo["plazo_efectivo_dias"]) == (7, 3, 10)
    assert (bueno["proveedor_id"], bueno["proveedor"]) == (1, "Distribuidora del Sur")
    assert (bueno["punto_reposicion"], malo["punto_reposicion"]) == (65.0, 80.0)
    assert malo["fecha_reposicion"] < bueno["fecha_reposicion"]
    assert malo["lote_sugerido"] > bueno["lote_sugerido"]
    assert bueno["comprar_ahora"] is False


def test_producto_sin_proveedor_usa_el_plazo_global(db_falsa_proveedores):
    sin_proveedor = {p["id"]: p for p in cliente.get("/api/predicciones", headers=_encabezados()).json()}[3]
    assert (sin_proveedor["lead_time_dias"], sin_proveedor["margen_dias"]) == (7, 0)
    assert sin_proveedor["proveedor_id"] is None and sin_proveedor["proveedor"] is None


def test_un_proveedor_con_datos_invalidos_no_tira_las_predicciones_de_los_demas(db_falsa_proveedores):
    respuesta = cliente.get("/api/predicciones", headers=_encabezados())

    assert respuesta.status_code == 200
    por_id = {p["id"]: p for p in respuesta.json()}
    assert len(por_id) == 4
    assert por_id[4]["proveedor_id"] is None and por_id[4]["lead_time_dias"] == 7


def test_prediccion_por_producto_y_proyeccion_incluyen_los_campos_nuevos(db_falsa_proveedores):
    individual = cliente.get("/api/predicciones/2", headers=_encabezados())
    assert individual.status_code == 200 and individual.json()["margen_dias"] == 3
    proyeccion = cliente.get("/api/predicciones/2/proyeccion", headers=_encabezados())
    assert proyeccion.status_code == 200 and proyeccion.json()["producto"]["punto_reposicion"] == 80.0


def test_alertas_conserva_su_contrato_con_los_campos_nuevos(db_falsa_proveedores):
    respuesta = cliente.get("/api/alertas", headers=_encabezados())
    assert respuesta.status_code == 200
    assert all({"criticidad", "lote_sugerido", "comprar_ahora"} <= set(p) for p in respuesta.json())


def test_parametros_por_defecto_incluyen_los_de_reposicion():
    assert config.PARAMETROS_POR_DEFECTO["margen_dias_proveedor_bajo"] == 3
    assert config.PARAMETROS_POR_DEFECTO["clasificacion_proveedor_baja"] == 2
