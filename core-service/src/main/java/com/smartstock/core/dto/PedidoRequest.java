package com.smartstock.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.smartstock.core.model.FormaPago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record PedidoRequest(
        @NotNull @Positive Long clienteId,
        @NotNull FormaPago formaPago,
        @DecimalMin("0") @DecimalMax("20") BigDecimal descuentoPct,
        LocalDate fechaEnvio,
        @NotEmpty List<@NotNull @Valid Item> items) {

    public record Item(@NotNull @Positive Long productoId, @NotNull @Positive Integer cantidad) {
    }
}
