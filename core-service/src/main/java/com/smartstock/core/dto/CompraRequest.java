package com.smartstock.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ingreso de mercadería. Si se informa costoUnitario, se actualiza el costo del producto. */
public record CompraRequest(
        @NotNull(message = "El producto es obligatorio") Long productoId,
        @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad debe ser mayor que cero") Integer cantidad,
        @DecimalMin(value = "0.0", message = "El costo no puede ser negativo") BigDecimal costoUnitario,
        @Size(max = 255, message = "La observación admite hasta 255 caracteres") String observacion,
        Long proveedorId,
        LocalDate fechaVencimiento,
        LocalDate fechaEntrega) {
}
