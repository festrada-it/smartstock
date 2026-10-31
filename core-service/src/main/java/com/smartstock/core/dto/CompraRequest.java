package com.smartstock.core.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ingreso de mercadería. Si se informa costoUnitario, se actualiza el costo del producto. */
public record CompraRequest(
        @NotNull Long productoId,
        @NotNull @Min(1) Integer cantidad,
        @DecimalMin("0.0") BigDecimal costoUnitario,
        @Size(max = 255) String observacion) {
}
