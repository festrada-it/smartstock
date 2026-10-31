from datetime import date, timedelta

import pytest

from app.reposicion import analizar_reposicion, plazo_reposicion

HOY = date(2026, 10, 31)


@pytest.mark.parametrize(
    "clasificacion,calidad,plazo",
    [(5, "BUENA", 7), (3, "REGULAR", 7), (2, "BUENA", 10),
     (1, "REGULAR", 10), (5, "MALA", 10), (1, "MALA", 10)],
)
def test_margen_por_clasificacion_o_calidad_sin_duplicarlo(clasificacion, calidad, plazo):
    assert plazo_reposicion(7, clasificacion, calidad, 4, 3, 2) == plazo


def test_sin_proveedor_usa_plazo_global_sin_margen():
    assert plazo_reposicion(None, None, None, 4, 3, 2) == 4


def test_limite_y_margen_configurables():
    assert plazo_reposicion(7, 3, "BUENA", 4, 5, 3) == 12


def test_proveedor_malo_dispara_compra_antes():
    bueno = plazo_reposicion(7, 5, "BUENA", 7, 3, 2)
    malo = plazo_reposicion(7, 1, "MALA", 7, 3, 2)
    resultado_bueno = analizar_reposicion([5.0] * 60, 80, 30, 7, bueno, HOY)
    resultado_malo = analizar_reposicion([5.0] * 60, 80, 30, 7, malo, HOY)
    assert resultado_bueno["fecha_reposicion"] == HOY + timedelta(days=3)
    assert resultado_bueno["comprar_ahora"] is False
    assert resultado_malo == {
        "lead_time_dias": 7, "punto_reposicion": 80.0,
        "fecha_reposicion": HOY, "comprar_ahora": True,
    }


@pytest.mark.parametrize("stock", [0, 64, 65])
def test_stock_menor_o_igual_al_punto_compra_hoy(stock):
    resultado = analizar_reposicion([5.0] * 60, stock, 30, 7, 7, HOY)
    assert resultado["comprar_ahora"] is True
    assert resultado["fecha_reposicion"] == HOY


def test_primer_dia_futuro_es_manana_y_punto_permanece_fijo():
    resultado = analizar_reposicion([2.0, 10.0, 1.0], 8, 0, 3, 3, HOY)
    assert resultado["punto_reposicion"] == 6
    assert resultado["fecha_reposicion"] == HOY + timedelta(days=1)


def test_cruce_en_ultimo_dia_del_horizonte():
    assert analizar_reposicion([1.0] * 60, 61, 0, 1, 1, HOY)["fecha_reposicion"] == HOY + timedelta(days=60)


def test_sin_cruce_en_horizonte():
    resultado = analizar_reposicion([1.0] * 60, 1000, 30, 7, 7, HOY)
    assert resultado["comprar_ahora"] is False
    assert resultado["fecha_reposicion"] is None


def test_sin_historial_no_afirma_que_no_hay_que_comprar():
    assert analizar_reposicion(None, 0, 30, 7, 10, HOY) == {
        "lead_time_dias": 7, "punto_reposicion": None,
        "fecha_reposicion": None, "comprar_ahora": None,
    }


def test_demanda_cero_sigue_considerando_stock_minimo():
    assert analizar_reposicion([0.0] * 60, 10, 30, 7, 7, HOY)["comprar_ahora"] is True
    assert analizar_reposicion([0.0] * 60, 31, 30, 7, 7, HOY)["fecha_reposicion"] is None


def test_no_redondea_antes_de_comparar_ni_modifica_demanda():
    demanda = [0.333] * 60
    resultado = analizar_reposicion(demanda, 1, 0, 3, 3, HOY)
    assert resultado["punto_reposicion"] == pytest.approx(0.999)
    assert resultado["comprar_ahora"] is False
    assert resultado["fecha_reposicion"] == HOY + timedelta(days=1)
    assert demanda == [0.333] * 60


@pytest.mark.parametrize("demanda", [[], [-1.0], [float("nan")], [float("inf")]])
def test_demanda_invalida_produce_error(demanda):
    with pytest.raises(ValueError, match="demanda proyectada"):
        analizar_reposicion(demanda, 10, 0, 7, 7, HOY)


@pytest.mark.parametrize("stock,minimo,lead,efectivo", [(-1, 0, 7, 7), (1, -1, 7, 7), (1, 0, -1, 0), (1, 0, 7, 6)])
def test_stock_o_plazos_invalidos_producen_error(stock, minimo, lead, efectivo):
    with pytest.raises(ValueError):
        analizar_reposicion([1.0], stock, minimo, lead, efectivo, HOY)


@pytest.mark.parametrize(
    "lead,clasificacion,calidad,global_dias,margen,limite",
    [(-1, 5, "BUENA", 7, 3, 2), (7, None, "BUENA", 7, 3, 2),
     (7, 6, "BUENA", 7, 3, 2), (7, 5, "OTRA", 7, 3, 2),
     (7, 5, "BUENA", -1, 3, 2), (7, 5, "BUENA", 7, -1, 2),
     (7, 5, "BUENA", 7, 3, 0)],
)
def test_datos_de_proveedor_invalidos_producen_error(lead, clasificacion, calidad, global_dias, margen, limite):
    with pytest.raises(ValueError):
        plazo_reposicion(lead, clasificacion, calidad, global_dias, margen, limite)
