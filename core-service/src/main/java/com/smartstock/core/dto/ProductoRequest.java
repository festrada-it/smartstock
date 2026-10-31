package com.smartstock.core.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductoRequest(
        @NotBlank @Size(max = 50) String sku,
        @NotBlank @Size(max = 150) String nombre,
        Long categoriaId,
        @NotNull @DecimalMin("0.0") BigDecimal precio,
        @NotNull @DecimalMin("0.0") BigDecimal costo,
        @NotNull @Min(0) Integer stockActual,
        @NotNull @Min(0) Integer stockMinimo) {
}
