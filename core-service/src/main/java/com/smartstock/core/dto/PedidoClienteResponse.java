package com.smartstock.core.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PedidoClienteResponse(Long id,LocalDateTime fecha,BigDecimal total,boolean anulada) {
}
