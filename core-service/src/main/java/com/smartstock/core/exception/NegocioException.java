package com.smartstock.core.exception;

/** Error de regla de negocio (ej. stock insuficiente). Se devuelve como 400. */
public class NegocioException extends RuntimeException {

    public NegocioException(String mensaje) {
        super(mensaje);
    }
}
