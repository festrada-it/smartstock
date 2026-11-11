"""Fill Rate por cadena, independiente de la base de datos."""
from decimal import Decimal

VERDE_DESDE = 98.0
AMARILLO_DESDE = 95.0
_ORDEN_SEMAFORO = {"ROJO": 0, "AMARILLO": 1, "VERDE": 2}


def semaforo(fill_rate: float | None) -> str | None:
    """Umbrales fijos: el objetivo configurable solo mueve la línea del gráfico."""
    if fill_rate is None:
        return None
    if fill_rate >= VERDE_DESDE:
        return "VERDE"
    if fill_rate >= AMARILLO_DESDE:
        return "AMARILLO"
    return "ROJO"


def calcular_fill_rate(vendidas: int, perdidas: int) -> float | None:
    """Porcentaje de unidades entregadas. Sin líneas devuelve None: no es 100 % ni 0 %."""
    if vendidas < 0 or perdidas < 0:
        raise ValueError("Las unidades vendidas y perdidas no pueden ser negativas")
    pedidas = vendidas + perdidas
    if pedidas == 0:
        return None
    return round(vendidas / pedidas * 100, 2)


def _filtrar(lineas: list[dict], grupo_id: int | None, cliente_id: int | None) -> list[dict]:
    return [l for l in lineas
            if (grupo_id is None or l["grupo_id"] == grupo_id)
            and (cliente_id is None or l["cliente_id"] == cliente_id)]


def _monto(valor: Decimal | float) -> float:
    return round(float(valor), 2)


def _medicion(vendidas: int, perdidas: int, monto: Decimal | float) -> dict:
    fill_rate = calcular_fill_rate(vendidas, perdidas)
    return {"vendidas": vendidas, "perdidas": perdidas, "fill_rate": fill_rate,
            "ventas_perdidas_monto": _monto(monto), "semaforo": semaforo(fill_rate)}


def totalizar(vendidas: list[dict], perdidas: list[dict],
              grupo_id: int | None = None, cliente_id: int | None = None) -> dict:
    """Fill Rate total de un período.

    `vendidas`: líneas con `cliente_id`, `grupo_id` y `unidades`.
    `perdidas`: líneas con `cliente_id`, `grupo_id`, `faltante` y `monto` (faltante × precio unitario)."""
    vendidas = _filtrar(vendidas, grupo_id, cliente_id)
    perdidas = _filtrar(perdidas, grupo_id, cliente_id)
    return _medicion(sum(l["unidades"] for l in vendidas), sum(l["faltante"] for l in perdidas),
                     sum((l["monto"] for l in perdidas), Decimal(0)))


def por_cliente(vendidas: list[dict], perdidas: list[dict],
                grupo_id: int | None = None, cliente_id: int | None = None) -> list[dict]:
    """Fill Rate de cada cliente con vendidas o perdidas, de peor a mejor."""
    vendidas = _filtrar(vendidas, grupo_id, cliente_id)
    perdidas = _filtrar(perdidas, grupo_id, cliente_id)
    clientes: dict[int | None, dict] = {}
    for linea in vendidas + perdidas:
        clientes.setdefault(linea["cliente_id"], {"cliente": linea["cliente"], "vendidas": 0, "perdidas": 0,
                                                  "monto": Decimal(0)})
    for linea in vendidas:
        clientes[linea["cliente_id"]]["vendidas"] += linea["unidades"]
    for linea in perdidas:
        clientes[linea["cliente_id"]]["perdidas"] += linea["faltante"]
        clientes[linea["cliente_id"]]["monto"] += linea["monto"]

    resultado = [{"cliente_id": id_cliente, "cliente": datos["cliente"],
                  **_medicion(datos["vendidas"], datos["perdidas"], datos["monto"])}
                 for id_cliente, datos in clientes.items()]
    return sorted(resultado, key=lambda c: (_ORDEN_SEMAFORO.get(c["semaforo"], 3), c["fill_rate"] or 0, c["cliente"]))


def por_producto(perdidas: list[dict], grupo_id: int | None = None, cliente_id: int | None = None) -> list[dict]:
    """Productos con ventas perdidas, de mayor a menor monto."""
    productos: dict[int, dict] = {}
    for linea in _filtrar(perdidas, grupo_id, cliente_id):
        datos = productos.setdefault(linea["producto_id"], {"producto": linea["producto"], "perdidas": 0,
                                                            "monto": Decimal(0)})
        datos["perdidas"] += linea["faltante"]
        datos["monto"] += linea["monto"]
    resultado = [{"producto_id": id_producto, "producto": datos["producto"], "perdidas": datos["perdidas"],
                  "ventas_perdidas_monto": _monto(datos["monto"])} for id_producto, datos in productos.items()]
    return sorted(resultado, key=lambda p: (-p["ventas_perdidas_monto"], p["producto"]))
