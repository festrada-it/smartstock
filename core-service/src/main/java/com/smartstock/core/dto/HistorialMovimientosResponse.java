package com.smartstock.core.dto;

import java.util.List;

public record HistorialMovimientosResponse(List<MovimientoStockResponse> contenido, int pagina,
        int tamanio, long totalElementos, int totalPaginas) {
}
