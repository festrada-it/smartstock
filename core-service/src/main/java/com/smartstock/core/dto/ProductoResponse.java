package com.smartstock.core.dto;

import java.math.BigDecimal;

import com.smartstock.core.model.Producto;

public record ProductoResponse(
        Long id,
        String sku,
        String nombre,
        Long categoriaId,
        String categoriaNombre,
        BigDecimal precio,
        BigDecimal costo,
        Integer stockActual,
        Integer stockMinimo,
        EstadoStock estadoStock,
        String marca,
        Long proveedorId,
        String proveedor,
        String grupo) {

    public enum EstadoStock { SIN_STOCK, BAJO_MINIMO, OK }

    public static ProductoResponse desde(Producto p) {
        return new ProductoResponse(
                p.getId(),
                p.getSku(),
                p.getNombre(),
                p.getCategoria() != null ? p.getCategoria().getId() : null,
                p.getCategoria() != null ? p.getCategoria().getNombre() : null,
                p.getPrecio(),
                p.getCosto(),
                p.getStockActual(),
                p.getStockMinimo(),
                estado(p),p.getMarca(),
                p.getProveedor()!=null?p.getProveedor().getId():null,
                p.getProveedor()!=null?p.getProveedor().getNombre():null,
                p.getCategoria()!=null?p.getCategoria().getNombre():null);
    }

    private static EstadoStock estado(Producto p) {
        if (p.getStockActual() == 0) {
            return EstadoStock.SIN_STOCK;
        }
        return p.getStockActual() < p.getStockMinimo() ? EstadoStock.BAJO_MINIMO : EstadoStock.OK;
    }
}
