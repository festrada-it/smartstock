package com.smartstock.core.dto;

import java.util.List;

public record HistorialClienteResponse(List<PedidoClienteResponse> contenido,int pagina,int tamanio,long totalElementos,int totalPaginas) {
}
