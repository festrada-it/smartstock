import numpy as np

from app.prediccion import (ALTA, BAJA, MEDIA, analizar_producto, clasificar_criticidad, dias_hasta_quiebre,
                            lote_sugerido, proyectar_demanda)

PARAMETROS = {"dias_reposicion": 7, "dias_cobertura": 30, "umbral_criticidad_alta": 7, "umbral_criticidad_media": 15}


def test_demanda_constante_se_proyecta_constante():
    demanda = proyectar_demanda([5.0] * 30, dias=10)
    assert np.allclose(demanda, 5.0)


def test_demanda_creciente_proyecta_tendencia():
    demanda = proyectar_demanda([float(i) for i in range(30)], dias=3)
    assert np.allclose(demanda, [30.0, 31.0, 32.0])


def test_demanda_nunca_es_negativa():
    demanda = proyectar_demanda([float(30 - i) for i in range(30)], dias=60)
    assert (demanda >= 0).all()


def test_historial_insuficiente_no_proyecta():
    assert proyectar_demanda([4.0], dias=10) is None


def test_dias_hasta_quiebre_con_demanda_constante():
    # 5 unidades por día con 48 en stock -> se agota al día 10
    assert dias_hasta_quiebre(np.full(60, 5.0), stock_actual=48) == 10


def test_sin_stock_quiebre_inmediato():
    assert dias_hasta_quiebre(np.full(60, 3.0), stock_actual=0) == 0


def test_sin_ventas_no_hay_quiebre_en_el_horizonte():
    assert dias_hasta_quiebre(np.zeros(60), stock_actual=10) is None


def test_criticidad():
    assert clasificar_criticidad(3, 10, 5, 7, 15) == ALTA
    assert clasificar_criticidad(12, 10, 5, 7, 15) == MEDIA
    assert clasificar_criticidad(40, 10, 5, 7, 15) == BAJA
    assert clasificar_criticidad(None, 4, 5, 7, 15) == MEDIA  # bajo el stock mínimo


def test_lote_sugerido_cubre_reposicion_cobertura_y_stock_minimo():
    # 2 u/día durante 7 + 30 días = 74, + 10 de mínimo - 20 en stock = 64
    assert lote_sugerido(np.full(60, 2.0), stock_actual=20, stock_minimo=10, dias_reposicion=7, dias_cobertura=30) == 64


def test_lote_sugerido_nunca_es_negativo():
    assert lote_sugerido(np.full(60, 1.0), stock_actual=500, stock_minimo=10, dias_reposicion=7, dias_cobertura=30) == 0


def test_analizar_producto_integra_todo():
    resultado = analizar_producto([5.0] * 90, stock_actual=20, stock_minimo=10, parametros=PARAMETROS)
    assert resultado["dias_hasta_quiebre"] == 4
    assert resultado["criticidad"] == ALTA
    assert resultado["lote_sugerido"] == 5 * 37 + 10 - 20
    assert resultado["demanda_diaria_estimada"] == 5.0
