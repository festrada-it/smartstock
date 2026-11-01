package com.smartstock.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.TreeMap;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstock.core.dto.*;
import com.smartstock.core.exception.*;
import com.smartstock.core.model.*;
import com.smartstock.core.repository.*;

@Service
@Transactional(readOnly = true)
public class PedidoService {
    private final VentaRepository ventas;
    private final ClienteRepository clientes;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;
    private final MovimientoStockRepository movimientos;
    private final VentaService servicioVentas;

    public PedidoService(VentaRepository ventas, ClienteRepository clientes,
                         ProductoRepository productos, UsuarioRepository usuarios,
                         MovimientoStockRepository movimientos, VentaService servicioVentas) {
        this.ventas = ventas;
        this.clientes = clientes;
        this.productos = productos;
        this.usuarios = usuarios;
        this.movimientos = movimientos;
        this.servicioVentas = servicioVentas;
    }

    @Transactional
    public PedidoResponse crear(PedidoRequest request, String email) {
        var venta = new Venta();
        venta.setEstado(EstadoPedido.PENDIENTE);
        venta.setUsuario(usuario(email));
        venta.setCliente(clientes.findById(request.clienteId()).filter(Cliente::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente " + request.clienteId() + " no encontrado")));
        if (request.fechaEnvio() != null && request.fechaEnvio().isBefore(venta.getFecha().toLocalDate())) {
            throw new NegocioException("La fecha de envío no puede ser anterior a la fecha del pedido");
        }
        venta.setFechaEnvio(request.fechaEnvio());
        venta.setFormaPago(request.formaPago());
        venta.setDescuentoPct(request.descuentoPct() == null ? BigDecimal.ZERO : request.descuentoPct());
        if (venta.getDescuentoPct().signum() < 0 || venta.getDescuentoPct().compareTo(new BigDecimal("20")) > 0) {
            throw new NegocioException("El descuento debe estar entre 0 y 20");
        }
        var cantidades = new TreeMap<Long, Integer>();
        for (var item : request.items()) {
            try {
                cantidades.merge(item.productoId(), item.cantidad(), Math::addExact);
            } catch (ArithmeticException e) {
                throw new NegocioException("La cantidad del producto supera el máximo permitido");
            }
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var item : cantidades.entrySet()) {
            var producto = productos.findById(item.getKey()).filter(p -> Boolean.TRUE.equals(p.getActivo()))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + item.getKey() + " no encontrado"));
            var detalle = new DetalleVenta(producto, item.getValue(), producto.getPrecio());
            venta.agregarDetalle(detalle);
            subtotal = subtotal.add(detalle.getSubtotal());
        }
        if (subtotal.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new NegocioException("El importe del pedido supera el máximo permitido");
        }
        venta.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        venta.setTotal(subtotal.multiply(BigDecimal.ONE.subtract(venta.getDescuentoPct().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP));
        servicioVentas.asignarNumeroOrden(venta);
        return PedidoResponse.desde(ventas.save(venta));
    }

    @Transactional
    public PedidoResponse confirmar(Long id, String email) {
        var venta = ventas.findByIdParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido " + id + " no encontrado"));
        if (venta.getEstado() != EstadoPedido.PENDIENTE) {
            throw new NegocioException("Solo se puede confirmar un pedido PENDIENTE");
        }
        var usuario = usuario(email);
        var bloqueados = servicioVentas.bloquearProductos(venta.getDetalles().stream()
                .map(d -> d.getProducto().getId()).toList());
        var advertencias = new ArrayList<String>();
        var salidas = servicioVentas.descontarStock(venta, usuario, bloqueados);
        for (var salida : salidas) {
            var producto = salida.getProducto();
            if (producto.getStockActual() <= producto.getStockMinimo()) {
                advertencias.add("Producto " + producto.getId() + " (" + producto.getNombre()
                        + "): stock " + producto.getStockActual() + " menor o igual al mínimo " + producto.getStockMinimo());
            }
        }
        venta.setEstado(EstadoPedido.CONFIRMADO);
        movimientos.saveAll(salidas);
        return PedidoResponse.desde(venta, advertencias);
    }

    public PedidoResponse obtener(Long id) {
        return PedidoResponse.desde(ventas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido " + id + " no encontrado")));
    }

    public PaginaPedidosResponse listar(EstadoPedido estado, Long clienteId, LocalDate desde,
                                       LocalDate hasta, String numeroOrden, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new NegocioException("La página debe ser válida y el tamaño debe estar entre 1 y 100");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new NegocioException("Desde no puede ser posterior a hasta");
        }
        Specification<Venta> filtros = (root, query, cb) -> {
            var condiciones = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (estado != null) condiciones.add(cb.equal(root.get("estado"), estado));
            if (clienteId != null) condiciones.add(cb.equal(root.get("cliente").get("id"), clienteId));
            if (desde != null) condiciones.add(cb.greaterThanOrEqualTo(root.get("fecha"), desde.atStartOfDay()));
            if (hasta != null) condiciones.add(cb.lessThanOrEqualTo(root.get("fecha"), hasta.atTime(java.time.LocalTime.MAX)));
            if (numeroOrden != null) condiciones.add(cb.equal(root.get("numeroOrden"), numeroOrden));
            return cb.and(condiciones.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var pagina = ventas.findAll(filtros, PageRequest.of(page, size, Sort.by("fecha", "id").descending()));
        var contenido = pagina.getContent().stream().map(PedidoResponse::desde).toList();
        return new PaginaPedidosResponse(contenido, page, size, pagina.getTotalElements(), pagina.getTotalPages());
    }

    private Usuario usuario(String email) {
        return usuarios.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + email + " no encontrado"));
    }
}
