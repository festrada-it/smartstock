package com.smartstock.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.smartstock.core.model.Lote;

public record LoteResponse(Long id, Long productoId, Long proveedorId, String proveedor,
        Integer cantidadInicial, Integer cantidadDisponible, LocalDate fechaVencimiento,
        LocalDate fechaEntrega, BigDecimal costoUnitario, boolean esApertura) {
    public static LoteResponse desde(Lote l) {
        var proveedor = l.getProveedor();
        return new LoteResponse(l.getId(), l.getProducto().getId(),
                proveedor == null ? null : proveedor.getId(), proveedor == null ? null : proveedor.getNombre(),
                l.getCantidadInicial(), l.getCantidadDisponible(), l.getFechaVencimiento(),
                l.getFechaEntrega(), l.getCostoUnitario(), l.isEsApertura());
    }
}
