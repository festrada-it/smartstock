import os

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql+psycopg://smartstock:smartstock@localhost:5432/smartstock")

# Debe ser el mismo secreto que usa core-service para firmar los tokens
JWT_SECRET = os.getenv("JWT_SECRET", "smartstock-secreto-de-desarrollo-cambiar-en-produccion-0123456789abcdef")

CORS_ORIGENES = os.getenv(
    "CORS_ORIGENES", "http://localhost:3000,http://127.0.0.1:5500,http://localhost:5500"
).split(",")

# Días de historial de ventas con los que se entrena el modelo
DIAS_HISTORIAL = 90

PARAMETROS_POR_DEFECTO = {
    "dias_reposicion": 7,
    "dias_cobertura": 30,
    "umbral_criticidad_alta": 7,
    "umbral_criticidad_media": 15,
}
