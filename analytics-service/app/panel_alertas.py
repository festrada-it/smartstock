"""Armado puro del panel, sin modificar las alertas del dashboard."""
from typing import Literal, TypedDict

Criticidad = Literal["ALTA", "MEDIA", "BAJA"]
TipoAlerta = Literal["QUIEBRE", "REPOSICION", "STOCK_MINIMO", "VENCIMIENTO", "BAJA_ROTACION"]

_ORDEN_CRITICIDAD = {"ALTA": 0, "MEDIA": 1, "BAJA": 2}
_TIPOS = ("QUIEBRE", "REPOSICION", "STOCK_MINIMO", "VENCIMIENTO", "BAJA_ROTACION")


class PrediccionPanel(TypedDict):
    id: int
    nombre: str
    criticidad: Criticidad
    dias_hasta_quiebre: int | None
    lote_sugerido: int
    comprar_ahora: bool | None
    stock_actual: int
    stock_minimo: int


class VencimientoPanel(TypedDict):
    producto_id: int
    producto: str
    lote_id: int
    dias_restantes: int
    criticidad: Criticidad
    accion_sugerida: str


class RotacionPanel(TypedDict):
    producto_id: int
    producto: str
    estado: Literal["SANA", "BAJA", "SIN_STOCK"]
    sugerencia: str


class AlertaPanel(TypedDict):
    tipo: TipoAlerta
    producto_id: int
    producto: str
    criticidad: Criticidad
    detalle: str
    accion_sugerida: str


def armar_panel(predicciones: list[PrediccionPanel], vencimientos: list[VencimientoPanel],
                rotaciones: list[RotacionPanel], tipo: TipoAlerta | None = None,
                criticidad: Criticidad | None = None) -> list[AlertaPanel]:
    if tipo is not None and tipo not in _TIPOS:
        raise ValueError("Tipo de alerta inválido")
    if criticidad is not None and criticidad not in _ORDEN_CRITICIDAD:
        raise ValueError("Criticidad inválida")

    alertas: list[AlertaPanel] = []
    for p in predicciones:
        if p["criticidad"] not in _ORDEN_CRITICIDAD:
            raise ValueError("Criticidad de predicción inválida")
        if p["criticidad"] in ("ALTA", "MEDIA"):
            dias = p["dias_hasta_quiebre"]
            detalle = (
                "Sin stock" if dias == 0 else
                "Riesgo de quiebre sin fecha estimada" if dias is None else
                f"Quiebre estimado en {dias} días"
            )
            alertas.append({
                "tipo": "QUIEBRE", "producto_id": p["id"], "producto": p["nombre"],
                "criticidad": p["criticidad"], "detalle": detalle,
                "accion_sugerida": f"Reponer {p['lote_sugerido']} unidades",
            })
        if p["comprar_ahora"] is True:
            alertas.append({
                "tipo": "REPOSICION", "producto_id": p["id"], "producto": p["nombre"],
                "criticidad": "MEDIA", "detalle": "El stock alcanzó el punto de reposición",
                "accion_sugerida": f"Comprar {p['lote_sugerido']} unidades",
            })
        if p["stock_actual"] < p["stock_minimo"]:
            alertas.append({
                "tipo": "STOCK_MINIMO", "producto_id": p["id"], "producto": p["nombre"],
                "criticidad": "MEDIA",
                "detalle": f"Stock {p['stock_actual']} por debajo del mínimo {p['stock_minimo']}",
                "accion_sugerida": "Reponer hasta cubrir el stock mínimo",
            })

    for v in vencimientos:
        if v["criticidad"] not in _ORDEN_CRITICIDAD:
            raise ValueError("Criticidad de vencimiento inválida")
        alertas.append({
            "tipo": "VENCIMIENTO", "producto_id": v["producto_id"], "producto": v["producto"],
            "criticidad": v["criticidad"],
            "detalle": f"Lote {v['lote_id']}: {v['dias_restantes']} días restantes",
            "accion_sugerida": v["accion_sugerida"],
        })

    for r in rotaciones:
        if r["estado"] not in ("SANA", "BAJA", "SIN_STOCK"):
            raise ValueError("Estado de rotación inválido")
        if r["estado"] == "BAJA":
            alertas.append({
                "tipo": "BAJA_ROTACION", "producto_id": r["producto_id"], "producto": r["producto"],
                "criticidad": "MEDIA", "detalle": "Rotación por debajo del umbral del grupo",
                "accion_sugerida": r["sugerencia"],
            })

    seleccionadas = [
        a for a in alertas
        if (tipo is None or a["tipo"] == tipo)
        and (criticidad is None or a["criticidad"] == criticidad)
    ]
    return sorted(seleccionadas, key=lambda a: _ORDEN_CRITICIDAD[a["criticidad"]])
