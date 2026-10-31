from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from app import db
from app.prediccion import estimar_quiebre

app = FastAPI(title="SmartStock - Analytics Service", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://127.0.0.1:5500"],
    allow_methods=["GET"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"servicio": "analytics-service", "estado": "OK"}


@app.get("/api/predicciones")
def predicciones():
    """Predicción de quiebre para todos los productos activos, ordenada por urgencia."""
    resultado = [_predecir(p) for p in db.obtener_productos()]
    return sorted(resultado, key=lambda r: (r["dias_hasta_quiebre"] is None, r["dias_hasta_quiebre"] or 0))


@app.get("/api/predicciones/{producto_id}")
def prediccion_producto(producto_id: int):
    producto = db.obtener_producto(producto_id)
    if producto is None:
        raise HTTPException(status_code=404, detail="Producto no encontrado")
    return _predecir(producto)


def _predecir(producto: dict) -> dict:
    estimacion = estimar_quiebre(db.ventas_diarias(producto["id"]), producto["stock_actual"])
    return {**producto, **estimacion}
