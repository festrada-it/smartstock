package com.smartstock.core.dto;

/** Línea sin stock suficiente, tal como la recibe el front al rechazar un pedido o una venta. */
public record FaltanteStock(Long productoId, String producto, Integer cantidadPedida, Integer disponible, Integer faltante) {
}
