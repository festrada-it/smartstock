package com.smartstock.core.dto;

import java.time.LocalDate;

public record RetiroLoteResponse(Long loteId, Long productoId, String producto, LocalDate fechaVencimiento,
                                 Integer cantidadRetirada, Integer stockActual) {
}
