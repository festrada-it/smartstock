import logging
from datetime import date, timedelta

import numpy as np

from app.prediccion import (ALTA, BAJA, MEDIA, analizar_producto, clasificar_criticidad, dias_hasta_quiebre,
                            lote_sugerido, proyectar_demanda)

PARAMETROS = {"dias_reposicion": 7, "dias_cobertura": 30, "umbral_criticidad_alta": 7, "umbral_criticidad_media": 15}
PARAMETROS_REPOSICION = {**PARAMETROS, "margen_dias_proveedor_bajo": 3, "clasificacion_proveedor_baja": 2}


def _proveedor(id=1, nombre="Proveedor", lead_time_dias=7, clasificacion=5, calidad="BUENA"):
    return {"id": id, "nombre": nombre, "lead_time_dias": lead_time_dias, "clasificacion": clasificacion, "calidad": calidad}


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


def _analizar(proveedor, stock=100, parametros=PARAMETROS_REPOSICION):
    return analizar_producto([5.0] * 90, stock_actual=stock, stock_minimo=30, parametros=parametros, proveedor=proveedor)


def test_producto_igual_con_proveedor_malo_dispara_la_compra_antes_que_con_el_bueno():
    bueno = _analizar(_proveedor(clasificacion=5, calidad="BUENA"))
    malo = _analizar(_proveedor(clasificacion=1, calidad="MALA"))
    # punto = 5 u/día x (7 + margen) + 30; el margen de 3 días solo aplica al malo
    assert (bueno["punto_reposicion"], malo["punto_reposicion"]) == (65.0, 80.0)
    assert (bueno["margen_dias"], malo["margen_dias"]) == (0, 3)
    assert bueno["lead_time_dias"] == malo["lead_time_dias"] == 7
    assert bueno["fecha_reposicion"] == date.today() + timedelta(days=7)
    assert malo["fecha_reposicion"] == date.today() + timedelta(days=4)
    assert malo["fecha_reposicion"] < bueno["fecha_reposicion"]
    assert bueno["comprar_ahora"] is False and malo["comprar_ahora"] is False


def test_comprar_ahora_cuando_el_stock_ya_alcanzo_el_punto_del_proveedor_malo():
    # 80 u en stock: sobre el punto del proveedor bueno (65), justo en el del malo (80)
    assert _analizar(_proveedor(clasificacion=5, calidad="BUENA"), stock=80)["comprar_ahora"] is False
    malo = _analizar(_proveedor(clasificacion=1, calidad="MALA"), stock=80)
    assert malo["comprar_ahora"] is True
    assert malo["fecha_reposicion"] == date.today()


def test_lote_sugerido_usa_el_plazo_del_proveedor_mas_el_margen():
    bueno = _analizar(_proveedor(clasificacion=5, calidad="BUENA"))
    malo = _analizar(_proveedor(clasificacion=1, calidad="MALA"))
    assert bueno["lote_sugerido"] == 5 * (7 + 30) + 30 - 100
    assert malo["lote_sugerido"] == 5 * (7 + 3 + 30) + 30 - 100
    assert malo["plazo_efectivo_dias"] == 10 and bueno["plazo_efectivo_dias"] == 7


def test_proveedor_con_lead_time_distinto_del_parametro_global():
    resultado = _analizar(_proveedor(lead_time_dias=2, clasificacion=4, calidad="REGULAR"))
    assert resultado["lead_time_dias"] == 2 and resultado["margen_dias"] == 0
    assert resultado["punto_reposicion"] == 5 * 2 + 30
    assert resultado["lote_sugerido"] == 5 * (2 + 30) + 30 - 100


def test_sin_proveedor_usa_el_plazo_global_y_no_cambia_el_resultado_anterior():
    resultado = _analizar(None)
    assert (resultado["lead_time_dias"], resultado["margen_dias"], resultado["plazo_efectivo_dias"]) == (7, 0, 7)
    assert resultado["proveedor_id"] is None and resultado["proveedor"] is None
    assert resultado["lote_sugerido"] == 5 * (7 + 30) + 30 - 100
    assert resultado == _analizar(None, parametros=PARAMETROS_REPOSICION)


def test_identifica_al_proveedor_usado():
    resultado = _analizar(_proveedor(id=9, nombre="Limpieza Central"))
    assert (resultado["proveedor_id"], resultado["proveedor"]) == (9, "Limpieza Central")


def test_conserva_todos_los_campos_anteriores():
    anteriores = {"demanda_diaria_estimada", "dias_hasta_quiebre", "fecha_quiebre_estimada", "criticidad", "lote_sugerido"}
    nuevos = {"lead_time_dias", "margen_dias", "plazo_efectivo_dias", "proveedor_id", "proveedor",
              "punto_reposicion", "fecha_reposicion", "comprar_ahora"}
    assert (anteriores | nuevos) <= set(_analizar(_proveedor()))
    assert (anteriores | nuevos) <= set(_analizar(None))


def test_sin_historial_con_stock_bajo_el_minimo_compra_y_sin_stock_suficiente_no_afirma_nada():
    sin_historial = dict(ventas_diarias=[1.0], stock_minimo=30, parametros=PARAMETROS_REPOSICION, proveedor=_proveedor())
    bajo = analizar_producto(stock_actual=10, **sin_historial)
    assert (bajo["comprar_ahora"], bajo["punto_reposicion"]) == (True, None)
    alto = analizar_producto(stock_actual=500, **sin_historial)
    assert (alto["comprar_ahora"], alto["punto_reposicion"], alto["fecha_reposicion"]) == (None, None, None)


def test_proveedor_con_datos_invalidos_no_rompe_y_usa_el_plazo_global(caplog):
    with caplog.at_level(logging.WARNING, logger="app.prediccion"):
        resultado = _analizar(_proveedor(id=42, calidad="DESCONOCIDA"))
    assert resultado["lead_time_dias"] == 7 and resultado["margen_dias"] == 0
    assert resultado["proveedor_id"] is None
    assert "proveedor 42" in caplog.text


def test_parametros_globales_invalidos_no_rompen_la_prediccion(caplog):
    parametros = {**PARAMETROS_REPOSICION, "clasificacion_proveedor_baja": 0}
    with caplog.at_level(logging.WARNING, logger="app.prediccion"):
        resultado = _analizar(_proveedor(), parametros=parametros)
    assert resultado["comprar_ahora"] is False and resultado["proveedor_id"] is None
    assert caplog.text
