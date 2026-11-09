package com.smartstock.core.dto;

import jakarta.validation.constraints.Size;

/** Cuerpo opcional del retiro: por ejemplo, quién se llevó la mercadería o el número de remito. */
public record RetiroLoteRequest(
        @Size(max = 230, message = "La observación admite hasta 230 caracteres") String observacion) {
}
