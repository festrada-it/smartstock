package com.smartstock.core.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import jakarta.persistence.*;

@Entity
@Table(name = "lotes")
public class Lote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;
    @Column(name = "cantidad_inicial", nullable = false)
    private Integer cantidadInicial;
    @Column(name = "cantidad_disponible", nullable = false)
    private Integer cantidadDisponible;
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;
    @Column(name = "fecha_entrega", nullable = false)
    private LocalDate fechaEntrega;
    @Column(name = "costo_unitario", nullable = false)
    private BigDecimal costoUnitario;
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
    @Column(name = "es_apertura", nullable = false)
    private boolean esApertura;

    protected Lote() { }
    public Lote(Producto producto, Proveedor proveedor, int cantidad, LocalDate vencimiento,
                LocalDate entrega, BigDecimal costo, boolean apertura) {
        this.producto = producto;
        this.proveedor = proveedor;
        this.cantidadInicial = cantidad;
        this.cantidadDisponible = cantidad;
        this.fechaVencimiento = vencimiento;
        this.fechaEntrega = entrega;
        this.costoUnitario = costo;
        this.esApertura = apertura;
    }
    public Long getId() { return id; }
    public Producto getProducto() { return producto; }
    public Proveedor getProveedor() { return proveedor; }
    public Integer getCantidadInicial() { return cantidadInicial; }
    public Integer getCantidadDisponible() { return cantidadDisponible; }
    public void setCantidadDisponible(Integer cantidad) { this.cantidadDisponible = cantidad; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public boolean isEsApertura() { return esApertura; }
}
