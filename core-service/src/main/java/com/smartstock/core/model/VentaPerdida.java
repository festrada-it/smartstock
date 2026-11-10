package com.smartstock.core.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import jakarta.persistence.*;

/** Línea rechazada por falta de stock. Faltante = cantidad pedida - cantidad disponible. */
@Entity
@Table(name = "ventas_perdidas")
public class VentaPerdida {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;
    @Column(name = "cantidad_pedida", nullable = false)
    private Integer cantidadPedida;
    @Column(name = "cantidad_disponible", nullable = false)
    private Integer cantidadDisponible;
    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    protected VentaPerdida() { }

    public VentaPerdida(Cliente cliente, Usuario usuario, Producto producto, int cantidadPedida, int cantidadDisponible) {
        this.cliente = cliente;
        this.usuario = usuario;
        this.producto = producto;
        this.cantidadPedida = cantidadPedida;
        this.cantidadDisponible = cantidadDisponible;
        this.precioUnitario = producto.getPrecio();
    }
}
