package com.smartstock.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.smartstock.core.model.*;

public record PedidoResponse(
        Long id, String numeroOrden, LocalDateTime fecha,
        Long clienteId, String cliente, Calidad clienteCalidad, Integer clienteClasificacion,
        FormaPago formaPago, BigDecimal descuentoPct, boolean conDescuento,
        BigDecimal subtotal, BigDecimal importeDescuento, BigDecimal total,
        LocalDate fechaEnvio, EstadoPedido estado, EstadoEntrega estadoEntrega,
        String numeroFactura, List<Item> items, List<String> advertencias) {

    public record Item(Long productoId, String producto, String marca, Integer cantidad,
                       Integer cantidadSolicitada, Integer cantidadEntregada, Integer faltante,
                       BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    public static PedidoResponse desde(Venta venta) {
        return desde(venta, List.of());
    }

    public static PedidoResponse desde(Venta venta, List<String> advertencias) {
        var cliente = venta.getCliente();
        var items = venta.getDetalles().stream()
                .map(d -> new Item(d.getProducto().getId(), d.getProducto().getNombre(),
                        d.getProducto().getMarca(), d.getCantidad(), d.getCantidadSolicitada(), d.getCantidad(),
                        d.getCantidadSolicitada() - d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal()))
                .toList();
        return new PedidoResponse(venta.getId(), venta.getNumeroOrden(), venta.getFecha(),
                cliente == null ? null : cliente.getId(), cliente == null ? null : cliente.getNombre(),
                cliente == null ? null : cliente.getCalidad(), cliente == null ? null : cliente.getClasificacion(),
                venta.getFormaPago(), venta.getDescuentoPct(), venta.getDescuentoPct().signum() > 0,
                venta.getSubtotal(), venta.getSubtotal().subtract(venta.getTotal()), venta.getTotal(),
                venta.getFechaEnvio(), venta.getEstado(), venta.getEstadoEntrega(), venta.getNumeroFactura(),
                items, advertencias);
    }
}
