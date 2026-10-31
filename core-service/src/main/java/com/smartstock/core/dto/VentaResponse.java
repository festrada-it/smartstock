package com.smartstock.core.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.smartstock.core.model.Venta;

public record VentaResponse(Long id, LocalDateTime fecha, String vendedor, BigDecimal total, boolean anulada, List<Item> items) {

    public record Item(Long productoId, String producto, Integer cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    public static VentaResponse desde(Venta venta) {
        List<Item> items = venta.getDetalles().stream()
                .map(d -> new Item(d.getProducto().getId(), d.getProducto().getNombre(),
                        d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal()))
                .toList();
        return new VentaResponse(venta.getId(), venta.getFecha(), venta.getUsuario().getNombre(), venta.getTotal(), venta.isAnulada(), items);
    }
}
