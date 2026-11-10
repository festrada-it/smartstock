package com.smartstock.core.exception;

import java.util.List;

import com.smartstock.core.dto.FaltanteStock;

/**
 * Rechaza una operación por falta de stock. Se lanza antes de escribir el pedido, y las
 * transacciones la declaran en noRollbackFor para que la venta perdida quede guardada.
 */
public class StockInsuficienteException extends NegocioException {
    private final transient List<FaltanteStock> faltantes;

    public StockInsuficienteException(List<FaltanteStock> faltantes) {
        super("No hay stock suficiente");
        this.faltantes = List.copyOf(faltantes);
    }

    public List<FaltanteStock> getFaltantes() { return faltantes; }
}
