package com.smartstock.core.dto;

import java.time.LocalDateTime;

public record ProductoClienteResponse(Long productoId,String producto,String marca,Long unidadesCompradas,LocalDateTime ultimaCompra) {
}
