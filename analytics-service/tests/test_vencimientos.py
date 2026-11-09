from datetime import date, timedelta
from decimal import Decimal

import pytest

from app.vencimientos import analizar_vencimiento, listar_vencimientos

HOY = date(2026, 10, 31)


@pytest.mark.parametrize(
    "dias,ventana,criticidad,accion",
    [
        (-90, 60, "ALTA", "Retirar de la venta"),
        (-1, 60, "ALTA", "Retirar de la venta"),
        (0, 60, "ALTA", "Vender primero"),
        (7, 60, "ALTA", "Vender primero"),
        (8, 60, "MEDIA", "Vender primero"),
        (30, 60, "MEDIA", "Vender primero"),
        (31, 60, "BAJA", "Vender primero"),
        (60, 60, "BAJA", "Vender primero"),
        (7, 15, "ALTA", "Vender primero"),
        (8, 15, "BAJA", "Vender primero"),
        (8, 17, "MEDIA", "Vender primero"),
        (9, 17, "BAJA", "Vender primero"),
        (5, 10, "ALTA", "Vender primero"),
        (10, 10, "BAJA", "Vender primero"),
        (0, 0, "ALTA", "Vender primero"),
    ],
)
def test_criticidad_y_accion_segun_ventana_del_grupo(dias, ventana, criticidad, accion):
    fecha = HOY + timedelta(days=dias)

    resultado = analizar_vencimiento(fecha, 3, Decimal("12.35"), ventana, HOY)

    assert resultado == {
        "cantidad_disponible": 3,
        "fecha_vencimiento": fecha,
        "dias_restantes": dias,
        "valor_en_riesgo": Decimal("37.05"),
        "criticidad": criticidad,
        "accion_sugerida": accion,
    }


@pytest.mark.parametrize("dias,ventana", [(61, 60), (11, 10), (1, 0)])
def test_lote_fuera_de_la_ventana_no_genera_alerta(dias, ventana):
    assert analizar_vencimiento(HOY + timedelta(days=dias), 1, Decimal("10"), ventana, HOY) is None


@pytest.mark.parametrize("fecha,cantidad", [(None, 3), (HOY, 0), (HOY - timedelta(days=1), 0)])
def test_sin_vencimiento_o_sin_disponible_no_genera_alerta(fecha, cantidad):
    assert analizar_vencimiento(fecha, cantidad, Decimal("10"), 60, HOY) is None


def test_valor_en_riesgo_con_costo_cero():
    assert analizar_vencimiento(HOY, 3, Decimal("0"), 60, HOY)["valor_en_riesgo"] == Decimal("0")


@pytest.mark.parametrize(
    "cantidad,costo,ventana,mensaje",
    [
        (-1, Decimal("10"), 60, "cantidad disponible"),
        (1, Decimal("-0.01"), 60, "costo unitario"),
        (1, Decimal("NaN"), 60, "costo unitario"),
        (1, Decimal("Infinity"), 60, "costo unitario"),
        (1, Decimal("10"), -1, "dias de alerta"),
    ],
)
def test_datos_invalidos_producen_error_explicito(cantidad, costo, ventana, mensaje):
    with pytest.raises(ValueError, match=mensaje):
        analizar_vencimiento(HOY, cantidad, costo, ventana, HOY)


def _lote(lote_id, dias, ventana=60, disponible=10, costo="2.50"):
    return {"lote_id": lote_id, "producto_id": 1, "sku": "A", "producto": "Producto", "marca": "Marca",
            "grupo": "Grupo", "cantidad_disponible": disponible,
            "fecha_vencimiento": None if dias is None else HOY + timedelta(days=dias),
            "costo_unitario": Decimal(costo), "dias_alerta_vencimiento": ventana}


def test_listar_ordena_por_criticidad_y_luego_por_dias_restantes():
    lotes = [_lote(1, 50), _lote(2, 20), _lote(3, -5), _lote(4, 3), _lote(5, 20)]

    resultado = listar_vencimientos(lotes, HOY)

    assert [(a["lote_id"], a["criticidad"]) for a in resultado] == [
        (3, "ALTA"), (4, "ALTA"), (2, "MEDIA"), (5, "MEDIA"), (1, "BAJA")]


def test_listar_descarta_lotes_sin_alerta():
    lotes = [_lote(1, 61), _lote(2, None), _lote(3, 5, disponible=0), _lote(4, 60)]

    assert [a["lote_id"] for a in listar_vencimientos(lotes, HOY)] == [4]


def test_listar_incluye_los_datos_del_lote_y_el_valor_en_riesgo_con_el_costo_del_lote():
    alerta = listar_vencimientos([_lote(7, 10, ventana=30, disponible=4, costo="12.50")], HOY)[0]

    assert alerta["lote_id"] == 7
    assert (alerta["sku"], alerta["producto"], alerta["marca"], alerta["grupo"]) == ("A", "Producto", "Marca", "Grupo")
    assert alerta["valor_en_riesgo"] == Decimal("50.00")
    assert alerta["dias_alerta_grupo"] == 30
