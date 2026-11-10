package com.smartstock.core.dto;

import com.smartstock.core.model.Producto;

public record ProductoProveedorResponse(
        Long id,
        String sku,
        String nombre,
        String marca,
        String categoriaNombre,
        Integer stockActual,
        Integer stockMinimo) {

    public static ProductoProveedorResponse desde(Producto p) {
        return new ProductoProveedorResponse(
                p.getId(),
                p.getSku(),
                p.getNombre(),
                p.getMarca(),
                p.getCategoria() != null ? p.getCategoria().getNombre() : null,
                p.getStockActual(),
                p.getStockMinimo());
    }
}
