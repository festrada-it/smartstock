from copy import deepcopy

import pytest

from app.panel_alertas import armar_panel


@pytest.fixture
def fuentes():
    return (
        [
            {"id": 1, "nombre": "Yerba", "criticidad": "ALTA", "dias_hasta_quiebre": 2,
             "lote_sugerido": 50, "comprar_ahora": True, "stock_actual": 5, "stock_minimo": 30},
            {"id": 2, "nombre": "Arroz", "criticidad": "BAJA", "dias_hasta_quiebre": None,
             "lote_sugerido": 0, "comprar_ahora": False, "stock_actual": 100, "stock_minimo": 30},
        ],
        [
            {"producto_id": 2, "producto": "Arroz", "lote_id": 7, "dias_restantes": 20,
             "criticidad": "BAJA", "accion_sugerida": "Vender primero"},
            {"producto_id": 1, "producto": "Yerba", "lote_id": 8, "dias_restantes": -1,
             "criticidad": "ALTA", "accion_sugerida": "Retirar de la venta"},
        ],
        [
            {"producto_id": 2, "producto": "Arroz", "estado": "BAJA", "sugerencia": "Comprar menos"},
            {"producto_id": 1, "producto": "Yerba", "estado": "SANA",
             "sugerencia": "Seguir reponiendo según la predicción"},
        ],
    )


def test_armado_de_cinco_tipos_y_orden_sin_modificar_fuentes(fuentes):
    original = deepcopy(fuentes)
    panel = armar_panel(*fuentes)
    assert [a["tipo"] for a in panel] == [
        "QUIEBRE", "VENCIMIENTO", "REPOSICION", "STOCK_MINIMO", "BAJA_ROTACION", "VENCIMIENTO",
    ]
    assert [a["criticidad"] for a in panel] == ["ALTA", "ALTA", "MEDIA", "MEDIA", "MEDIA", "BAJA"]
    assert panel[0] == {
        "tipo": "QUIEBRE", "producto_id": 1, "producto": "Yerba", "criticidad": "ALTA",
        "detalle": "Quiebre estimado en 2 días", "accion_sugerida": "Reponer 50 unidades",
    }
    assert panel[1]["accion_sugerida"] == "Retirar de la venta"
    assert panel[4]["accion_sugerida"] == "Comprar menos"
    assert fuentes == original
    for alerta in panel:
        assert set(alerta) == {"tipo", "producto_id", "producto", "criticidad", "detalle", "accion_sugerida"}


@pytest.mark.parametrize("tipo", ["QUIEBRE", "REPOSICION", "STOCK_MINIMO", "VENCIMIENTO", "BAJA_ROTACION"])
def test_filtro_por_tipo(fuentes, tipo):
    panel = armar_panel(*fuentes, tipo=tipo)
    assert panel
    assert all(a["tipo"] == tipo for a in panel)


@pytest.mark.parametrize("criticidad", ["ALTA", "MEDIA", "BAJA"])
def test_filtro_por_criticidad(fuentes, criticidad):
    panel = armar_panel(*fuentes, criticidad=criticidad)
    assert panel
    assert all(a["criticidad"] == criticidad for a in panel)


def test_filtros_combinados(fuentes):
    panel = armar_panel(*fuentes, tipo="VENCIMIENTO", criticidad="ALTA")
    assert len(panel) == 1
    assert panel[0]["producto_id"] == 1
    assert armar_panel(*fuentes, tipo="QUIEBRE", criticidad="BAJA") == []


def test_fuentes_vacias():
    assert armar_panel([], [], []) == []


def test_igualdad_al_minimo_no_dispara_y_compra_desconocida_no_dispara(fuentes):
    p = fuentes[0][1]
    p["stock_actual"] = p["stock_minimo"]
    p["comprar_ahora"] = None
    assert armar_panel([p], [], []) == []
    p["stock_actual"] -= 1
    assert [a["tipo"] for a in armar_panel([p], [], [])] == ["STOCK_MINIMO"]


@pytest.mark.parametrize("dias,detalle", [(0, "Sin stock"), (None, "Riesgo de quiebre sin fecha estimada")])
def test_quiebre_conserva_logica_actual_de_alertas(fuentes, dias, detalle):
    p = fuentes[0][0]
    p["dias_hasta_quiebre"] = dias
    p["criticidad"] = "MEDIA"
    panel = armar_panel([p], [], [], tipo="QUIEBRE")
    assert panel[0]["detalle"] == detalle
    assert panel[0]["criticidad"] == "MEDIA"


def test_sin_stock_no_se_clasifica_como_baja_rotacion(fuentes):
    r = fuentes[2][0]
    r["estado"] = "SIN_STOCK"
    assert armar_panel([], [], [r]) == []


@pytest.mark.parametrize("filtros", [{"tipo": ""}, {"tipo": "OTRO"}, {"criticidad": ""}, {"criticidad": "URGENTE"}])
def test_filtros_invalidos_producen_error(filtros):
    with pytest.raises(ValueError):
        armar_panel([], [], [], **filtros)


@pytest.mark.parametrize("indice,campo,valor", [(0, "criticidad", "OTRA"), (1, "criticidad", "OTRA"), (2, "estado", "OTRO")])
def test_datos_de_origen_invalidos_producen_error(fuentes, indice, campo, valor):
    fuentes[indice][0][campo] = valor
    with pytest.raises(ValueError):
        armar_panel(*fuentes)
