package com.smartstock.core.model;

import jakarta.persistence.*;

/** Origen de cada fracción entregada; se conserva al anular para auditoría. */
@Entity
@Table(name = "consumos_lote")
public class ConsumoLote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detalle_venta_id")
    private DetalleVenta detalleVenta;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id")
    private Lote lote;
    @Column(nullable = false)
    private Integer cantidad;

    protected ConsumoLote() { }
    public ConsumoLote(DetalleVenta detalleVenta, Lote lote, int cantidad) {
        this.detalleVenta = detalleVenta;
        this.lote = lote;
        this.cantidad = cantidad;
    }
    public Long getId() { return id; }
    public DetalleVenta getDetalleVenta() { return detalleVenta; }
    public Lote getLote() { return lote; }
    public Integer getCantidad() { return cantidad; }
}
