package com.smartstock.core.dto;

import java.time.LocalDateTime;

import com.smartstock.core.model.MovimientoStock;
import com.smartstock.core.model.TipoMovimiento;

public record MovimientoStockResponse(Long id, Long productoId, TipoMovimiento tipo, Integer cantidad,
        LocalDateTime fecha, Long usuarioId, String usuario, Long ventaId, String observacion, Long loteId) {

    public static MovimientoStockResponse desde(MovimientoStock movimiento) {
        var usuario = movimiento.getUsuario();
        var venta = movimiento.getVenta();
        return new MovimientoStockResponse(movimiento.getId(), movimiento.getProducto().getId(),
                movimiento.getTipo(), movimiento.getCantidad(), movimiento.getFecha(),
                usuario == null ? null : usuario.getId(), usuario == null ? null : usuario.getNombre(),
                venta == null ? null : venta.getId(), movimiento.getObservacion(), movimiento.getLoteId());
    }
}
