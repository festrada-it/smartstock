"""Validación del token JWT emitido por core-service (mismo secreto compartido)."""
import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app import config

_esquema = HTTPBearer(auto_error=False)


def usuario_actual(credenciales: HTTPAuthorizationCredentials | None = Depends(_esquema)) -> dict:
    if credenciales is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Falta el token de autenticación")
    try:
        return jwt.decode(credenciales.credentials, config.JWT_SECRET, algorithms=["HS256", "HS384", "HS512"])
    except jwt.PyJWTError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token inválido o expirado")
