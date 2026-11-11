"""Fill Rate por cadena: regla pura, endpoint y consulta de las líneas."""
from contextlib import nullcontext
from datetime import datetime, timedelta
from decimal import Decimal
import os
from types import SimpleNamespace

import jwt
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text

from app import config, db
from app.fill_rate import calcular_fill_rate, por_cliente, por_producto, semaforo, totalizar
from app.main import app

cliente_http = TestClient(app)

DIA, CARREFOUR, JUMBO = 1, 2, 3
LACTEOS, CONGELADOS = 10, 20


def _venta(cliente_id, nombre, grupo_id, unidades, producto_id=1):
    return {"cliente_id": cliente_id, "cliente": nombre, "producto_id": producto_id, "producto": f"P{producto_id}",
            "grupo_id": grupo_id, "unidades": unidades}


def _perdida(cliente_id, nombre, grupo_id, faltante, precio, producto_id=1):
    return {"cliente_id": cliente_id, "cliente": nombre, "producto_id": producto_id, "producto": f"P{producto_id}",
            "grupo_id": grupo_id, "faltante": faltante, "monto": Decimal(faltante) * Decimal(precio)}


VENDIDAS = [_venta(DIA, "Día", LACTEOS, 99), _venta(CARREFOUR, "Carrefour", LACTEOS, 90, 1),
            _venta(CARREFOUR, "Carrefour", CONGELADOS, 60, 2), _venta(JUMBO, "Jumbo", LACTEOS, 100)]
PERDIDAS = [_perdida(DIA, "Día", LACTEOS, 1, "10"), _perdida(CARREFOUR, "Carrefour", LACTEOS, 10, "100", 1),
            _perdida(CARREFOUR, "Carrefour", CONGELADOS, 5, "50", 2)]


@pytest.mark.parametrize("valor,color", [
    (100.0, "VERDE"), (98.0, "VERDE"), (97.99, "AMARILLO"), (95.0, "AMARILLO"), (94.99, "ROJO"), (0.0, "ROJO"),
    (None, None)])
def test_semaforo_fijo_en_los_limites(valor, color):
    assert semaforo(valor) == color


def test_fill_rate_sin_ventas_perdidas_es_100():
    assert calcular_fill_rate(80, 0) == 100.0


def test_fill_rate_con_ventas_perdidas():
    assert calcular_fill_rate(90, 10) == 90.0
    assert calcular_fill_rate(2, 1) == 66.67


def test_fill_rate_sin_lineas_es_nulo_y_no_100_ni_0():
    assert calcular_fill_rate(0, 0) is None
    total = totalizar([], [])
    assert total == {"vendidas": 0, "perdidas": 0, "fill_rate": None, "ventas_perdidas_monto": 0.0, "semaforo": None}


def test_solo_ventas_perdidas_da_0():
    assert calcular_fill_rate(0, 5) == 0.0


def test_unidades_negativas_son_un_error():
    with pytest.raises(ValueError):
        calcular_fill_rate(-1, 0)


def test_total_suma_vendidas_perdidas_y_ventas_perdidas_en_pesos():
    total = totalizar(VENDIDAS, PERDIDAS)

    assert total["vendidas"] == 349 and total["perdidas"] == 16
    assert total["fill_rate"] == round(349 / 365 * 100, 2)
    assert total["ventas_perdidas_monto"] == 10 + 1000 + 250
    assert total["semaforo"] == "AMARILLO"  # 349 de 365 = 95,62 %


def test_total_filtra_por_cliente():
    total = totalizar(VENDIDAS, PERDIDAS, cliente_id=DIA)

    assert (total["vendidas"], total["perdidas"], total["fill_rate"], total["semaforo"]) == (99, 1, 99.0, "VERDE")


def test_total_filtra_por_grupo_con_el_producto_de_cada_linea():
    total = totalizar(VENDIDAS, PERDIDAS, grupo_id=CONGELADOS)

    assert (total["vendidas"], total["perdidas"]) == (60, 5)
    assert total["ventas_perdidas_monto"] == 250


def test_total_filtra_por_cliente_y_grupo_a_la_vez():
    total = totalizar(VENDIDAS, PERDIDAS, grupo_id=LACTEOS, cliente_id=CARREFOUR)

    assert (total["vendidas"], total["perdidas"], total["fill_rate"]) == (90, 10, 90.0)


def test_filtro_sin_coincidencias_da_nulo():
    assert totalizar(VENDIDAS, PERDIDAS, cliente_id=999)["fill_rate"] is None


def test_por_cliente_va_de_peor_a_mejor_con_los_tres_colores():
    vendidas = [_venta(1, "Verde", 1, 99), _venta(2, "Amarillo", 1, 96), _venta(3, "Rojo", 1, 90)]
    perdidas = [_perdida(1, "Verde", 1, 1, "1"), _perdida(2, "Amarillo", 1, 4, "1"), _perdida(3, "Rojo", 1, 10, "1")]

    resultado = por_cliente(vendidas, perdidas)

    assert [(c["cliente"], c["fill_rate"], c["semaforo"]) for c in resultado] == [
        ("Rojo", 90.0, "ROJO"), ("Amarillo", 96.0, "AMARILLO"), ("Verde", 99.0, "VERDE")]


def test_por_cliente_junta_productos_y_calcula_sus_pesos():
    carrefour = next(c for c in por_cliente(VENDIDAS, PERDIDAS) if c["cliente_id"] == CARREFOUR)

    assert carrefour == {"cliente_id": CARREFOUR, "cliente": "Carrefour", "vendidas": 150, "perdidas": 15,
                         "fill_rate": 90.91, "ventas_perdidas_monto": 1250.0, "semaforo": "ROJO"}


def test_por_cliente_incluye_a_quien_solo_tiene_ventas_perdidas():
    resultado = por_cliente([], [_perdida(5, "Nuevo", 1, 3, "2")])

    assert resultado[0]["fill_rate"] == 0.0 and resultado[0]["semaforo"] == "ROJO"


def test_por_cliente_con_filtro_de_cliente_solo_trae_ese_cliente():
    assert [c["cliente_id"] for c in por_cliente(VENDIDAS, PERDIDAS, cliente_id=JUMBO)] == [JUMBO]


def test_por_producto_ordena_por_ventas_perdidas_en_pesos():
    resultado = por_producto(PERDIDAS)

    assert [(p["producto_id"], p["perdidas"], p["ventas_perdidas_monto"]) for p in resultado] == [
        (1, 11, 1010.0), (2, 5, 250.0)]


def test_por_producto_filtra_por_grupo():
    assert [p["producto_id"] for p in por_producto(PERDIDAS, grupo_id=CONGELADOS)] == [2]


def _encabezados():
    token = jwt.encode({"sub": "admin@smartstock.com", "rol": "VENDEDOR"}, config.JWT_SECRET, algorithm="HS512")
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def lineas_falsas(monkeypatch):
    """Período actual con las líneas de arriba; el anterior, sin ventas perdidas."""
    llamadas = []

    def lineas(desde, hasta):
        llamadas.append((desde, hasta))
        if len(llamadas) == 1:
            return VENDIDAS, PERDIDAS
        return [_venta(DIA, "Día", LACTEOS, 50)], []

    monkeypatch.setattr(db, "lineas_fill_rate", lineas)
    monkeypatch.setattr(db, "obtener_parametros", lambda: {**config.PARAMETROS_POR_DEFECTO, "fill_rate_objetivo": 97.0})
    return llamadas


def test_sin_token_responde_401():
    assert cliente_http.get("/api/dashboard/fill-rate").status_code == 401


def test_endpoint_devuelve_el_contrato(lineas_falsas):
    respuesta = cliente_http.get("/api/dashboard/fill-rate", headers=_encabezados())

    assert respuesta.status_code == 200
    datos = respuesta.json()
    assert set(datos) == {"periodo", "objetivo", "total", "anterior", "por_cliente", "por_producto"}
    assert datos["periodo"] == 30 and datos["objetivo"] == 97.0
    assert datos["total"]["semaforo"] == "AMARILLO" and datos["total"]["perdidas"] == 16
    assert datos["anterior"] == {"vendidas": 50, "perdidas": 0, "fill_rate": 100.0,
                                 "ventas_perdidas_monto": 0.0, "semaforo": "VERDE"}
    assert [c["cliente"] for c in datos["por_cliente"]] == ["Carrefour", "Día", "Jumbo"]
    assert datos["por_producto"][0] == {"producto_id": 1, "producto": "P1", "perdidas": 11,
                                        "ventas_perdidas_monto": 1010.0}


def test_endpoint_consulta_el_periodo_y_el_anterior_de_igual_largo(lineas_falsas):
    cliente_http.get("/api/dashboard/fill-rate?periodo=90", headers=_encabezados())

    (desde, hasta), (desde_antes, hasta_antes) = lineas_falsas
    assert (hasta - desde).days == 90 and (hasta_antes - desde_antes).days == 90
    assert hasta_antes == desde
    assert (hasta - datetime.now().date()).days == 1  # incluye el día de hoy


def test_endpoint_filtra_por_cliente_y_grupo(lineas_falsas):
    datos = cliente_http.get(f"/api/dashboard/fill-rate?cliente_id={CARREFOUR}&grupo_id={CONGELADOS}",
                             headers=_encabezados()).json()

    assert (datos["total"]["vendidas"], datos["total"]["perdidas"]) == (60, 5)
    assert [c["cliente_id"] for c in datos["por_cliente"]] == [CARREFOUR]
    assert [p["producto_id"] for p in datos["por_producto"]] == [2]


def test_endpoint_sin_lineas_responde_nulos_y_listas_vacias(monkeypatch):
    monkeypatch.setattr(db, "lineas_fill_rate", lambda desde, hasta: ([], []))
    monkeypatch.setattr(db, "obtener_parametros", lambda: dict(config.PARAMETROS_POR_DEFECTO))

    datos = cliente_http.get("/api/dashboard/fill-rate", headers=_encabezados()).json()

    assert datos["objetivo"] == 95.0
    assert datos["total"]["fill_rate"] is None and datos["total"]["semaforo"] is None
    assert datos["por_cliente"] == [] and datos["por_producto"] == []


@pytest.mark.parametrize("consulta", ["periodo=7", "periodo=abc", "grupo_id=x", "cliente_id=x"])
def test_parametros_invalidos_responden_422(lineas_falsas, consulta):
    assert cliente_http.get(f"/api/dashboard/fill-rate?{consulta}", headers=_encabezados()).status_code == 422


@pytest.fixture
def lineas_reales(monkeypatch):
    if os.environ.get("SMARTSTOCK_INTEGRACION") != "true":
        pytest.skip("Requiere PostgreSQL; CI habilita SMARTSTOCK_INTEGRACION=true")
    with db.engine.connect() as conexion:
        # Tablas temporales: ocultan a las reales durante la prueba y no modifican ninguna base.
        conexion.execute(text("CREATE TEMP TABLE clientes (id BIGINT, nombre TEXT)"))
        conexion.execute(text("CREATE TEMP TABLE productos (id BIGINT, nombre TEXT, categoria_id BIGINT)"))
        conexion.execute(text("CREATE TEMP TABLE ventas (id BIGINT, fecha TIMESTAMP, estado TEXT, cliente_id BIGINT)"))
        conexion.execute(text("CREATE TEMP TABLE detalle_venta (venta_id BIGINT, producto_id BIGINT, cantidad INTEGER)"))
        conexion.execute(text("CREATE TEMP TABLE ventas_perdidas (fecha TIMESTAMP, cliente_id BIGINT, producto_id BIGINT, "
                              "cantidad_pedida INTEGER, cantidad_disponible INTEGER, precio_unitario NUMERIC)"))
        ayer = datetime.now() - timedelta(days=1)
        hace_un_ano = datetime.now() - timedelta(days=365)
        conexion.execute(text("INSERT INTO clientes VALUES (1,'Día'),(2,'Jumbo')"))
        conexion.execute(text("INSERT INTO productos VALUES (1,'Leche',10),(2,'Helado',20)"))
        conexion.execute(text("INSERT INTO ventas VALUES (1,:f,'ENTREGADO',1),(2,:f,'PENDIENTE',1),(3,:f,'CANCELADO',1),"
                              "(4,:f,'CONFIRMADO',2),(5,:f,'ENVIADO',NULL),(6,:viejo,'ENTREGADO',1)"),
                         {"f": ayer, "viejo": hace_un_ano})
        conexion.execute(text("INSERT INTO detalle_venta VALUES (1,1,10),(1,2,5),(2,1,99),(3,1,99),(4,1,7),(5,2,3),(6,1,100)"))
        conexion.execute(text("INSERT INTO ventas_perdidas VALUES (:f,1,1,15,10,20),(:f,1,1,8,5,20),(:viejo,1,1,50,0,20)"),
                         {"f": ayer, "viejo": hace_un_ano})
        monkeypatch.setattr(db, "engine", SimpleNamespace(connect=lambda: nullcontext(conexion)))
        yield
        conexion.rollback()


def test_consulta_cuenta_solo_pedidos_que_vendieron_y_agrupa_por_cliente_y_producto(lineas_reales):
    desde = (datetime.now() - timedelta(days=30)).date()
    vendidas, perdidas = db.lineas_fill_rate(desde, datetime.now().date() + timedelta(days=1))

    unidades = {(v["cliente_id"], v["producto_id"]): v["unidades"] for v in vendidas}
    assert unidades == {(1, 1): 10, (1, 2): 5, (2, 1): 7, (None, 2): 3}  # sin PENDIENTE, CANCELADO ni el pedido viejo
    assert {v["cliente"] for v in vendidas} == {"Día", "Jumbo", "Sin cliente"}
    assert {v["grupo_id"] for v in vendidas} == {10, 20}
    assert len(perdidas) == 1
    assert (perdidas[0]["faltante"], perdidas[0]["monto"]) == (8, Decimal("160"))  # (15-10) + (8-5) = 8 unidades a $20


def test_endpoint_completo_sobre_la_consulta(lineas_reales):
    datos = cliente_http.get("/api/dashboard/fill-rate", headers=_encabezados()).json()

    assert (datos["total"]["vendidas"], datos["total"]["perdidas"]) == (25, 8)
    assert datos["total"]["ventas_perdidas_monto"] == 160.0
    assert datos["anterior"]["vendidas"] == 0 and datos["anterior"]["perdidas"] == 0
    assert datos["anterior"]["fill_rate"] is None
    assert [p["producto"] for p in datos["por_producto"]] == ["Leche"]
