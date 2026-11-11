import pytest

from app.rotacion import analizar_rotacion


@pytest.mark.parametrize(
    "vendidas,umbral,estado,sugerencia",
    [
        (0, 0.10, "BAJA", "No recomprar / promocionar"),
        (4, 0.10, "BAJA", "No recomprar / promocionar"),
        (5, 0.10, "BAJA", "Comprar menos"),
        (9, 0.10, "BAJA", "Comprar menos"),
        (10, 0.10, "SANA", "Seguir reponiendo según la predicción"),
        (11, 0.10, "SANA", "Seguir reponiendo según la predicción"),
        (10, 0.30, "BAJA", "No recomprar / promocionar"),
        (0, 0, "SANA", "Seguir reponiendo según la predicción"),
    ],
)
def test_rotacion_respeta_umbral_del_grupo_y_sus_limites(vendidas, umbral, estado, sugerencia):
    assert analizar_rotacion(vendidas, [100.0] * 30, umbral) == {
        "unidades_vendidas": vendidas,
        "stock_promedio": 100.0,
        "rotacion": vendidas / 100,
        "umbral_rotacion": umbral,
        "estado": estado,
        "sugerencia": sugerencia,
    }


@pytest.mark.parametrize("vendidas", [0, 10])
def test_stock_promedio_cero_no_calcula_rotacion(vendidas):
    resultado = analizar_rotacion(vendidas, [0.0] * 30, 0.10)
    assert resultado["stock_promedio"] == 0
    assert resultado["rotacion"] is None
    assert resultado["estado"] == "SIN_STOCK"
    assert resultado["sugerencia"] == "Sin stock"


def test_promedio_incluye_dias_sin_stock_y_no_modifica_historial():
    historial = [0.0] * 15 + [200.0] * 15
    resultado = analizar_rotacion(10, historial, 0.10)
    assert resultado["stock_promedio"] == 100
    assert resultado["rotacion"] == 0.10
    assert historial == [0.0] * 15 + [200.0] * 15


@pytest.mark.parametrize("dias", [0, 29, 31])
def test_rechaza_periodos_distintos_de_30_dias(dias):
    with pytest.raises(ValueError, match="exactamente 30"):
        analizar_rotacion(10, [100.0] * dias, 0.10)


@pytest.mark.parametrize("stock", [-1, float("nan"), float("inf"), float("-inf")])
def test_rechaza_stock_invalido(stock):
    with pytest.raises(ValueError, match="stock histórico"):
        analizar_rotacion(10, [stock] + [100.0] * 29, 0.10)


@pytest.mark.parametrize("umbral", [-0.1, float("nan"), float("inf")])
def test_rechaza_umbral_invalido(umbral):
    with pytest.raises(ValueError, match="umbral de rotación"):
        analizar_rotacion(10, [100.0] * 30, umbral)


def test_rechaza_unidades_negativas():
    with pytest.raises(ValueError, match="unidades vendidas"):
        analizar_rotacion(-1, [100.0] * 30, 0.10)
