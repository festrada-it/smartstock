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
            subtotal = subtotal.add(detalle.getSubtotal());
            detalle.setCantidadEntregada(0);
            venta.agregarDetalle(detalle);
        }
        if (subtotal.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new NegocioException("El importe del pedido supera el máximo permitido");
        }
        venta.setSubtotal(BigDecimal.ZERO.setScale(2));
        venta.setTotal(BigDecimal.ZERO.setScale(2));
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
        boolean hayEntrega = false;
        var advertencias = new ArrayList<String>();
        for (var detalle : venta.getDetalles()) {
            var producto = bloqueados.get(detalle.getProducto().getId());
            int entregada = Math.min(detalle.getCantidadSolicitada(), producto.getStockActual());
            detalle.setCantidadEntregada(entregada);
            hayEntrega |= entregada > 0;
            int faltante = detalle.getCantidadSolicitada() - entregada;
            if (faltante > 0) {
                advertencias.add("Producto " + producto.getId() + " (" + producto.getNombre() + "): faltan "
                        + faltante + " unidades (solicitadas: " + detalle.getCantidadSolicitada()
                        + ", entregadas: " + entregada + ")");
            }
        }
        if (!hayEntrega) {
            throw new NegocioException("Ningún producto tiene stock disponible para confirmar el pedido");
        }
        var salidas = servicioVentas.descontarStock(venta, usuario, bloqueados);
        for (var salida : salidas) {
            var producto = salida.getProducto();
            if (producto.getStockActual() <= producto.getStockMinimo()) {
                advertencias.add("Producto " + producto.getId() + " (" + producto.getNombre()
                        + "): stock " + producto.getStockActual() + " menor o igual al mínimo " + producto.getStockMinimo());
            }
        }
        BigDecimal subtotal = venta.getDetalles().stream().map(DetalleVenta::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        venta.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        venta.setTotal(subtotal.multiply(BigDecimal.ONE.subtract(venta.getDescuentoPct().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP));
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
        if (LocalDate.MAX.equals(hasta)) {
            throw new NegocioException("La fecha hasta supera el máximo permitido");
        }
        String numeroBuscado = numeroOrden == null ? "" : numeroOrden.trim();
        Specification<Venta> filtros = (root, query, cb) -> {
            var condiciones = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (estado != null) condiciones.add(cb.equal(root.get("estado"), estado));
            if (clienteId != null) condiciones.add(cb.equal(root.get("cliente").get("id"), clienteId));
            if (desde != null) condiciones.add(cb.greaterThanOrEqualTo(root.get("fecha"), desde.atStartOfDay()));
            if (hasta != null) condiciones.add(cb.lessThan(root.get("fecha"), hasta.plusDays(1).atStartOfDay()));
            if (!numeroBuscado.isEmpty()) condiciones.add(cb.equal(root.get("numeroOrden"), numeroBuscado));
            return cb.and(condiciones.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var pagina = ventas.findAll(filtros, PageRequest.of(page, size, Sort.by("fecha", "id").descending()));
        java.util.Map<Long, PedidoResponse> porId = new java.util.HashMap<>();
        if (!pagina.isEmpty()) {
            var ids = pagina.getContent().stream().map(Venta::getId).toList();
            // El fetch de colección completa solo esta página; nunca recibe Pageable.
            ventas.findConDetallesByIds(ids).forEach(v -> porId.put(v.getId(), PedidoResponse.desde(v)));
        }
        var contenido = pagina.getContent().stream().map(v -> porId.get(v.getId())).toList();
        return new PaginaPedidosResponse(contenido, page, size, pagina.getTotalElements(), pagina.getTotalPages());
    }

    private Usuario usuario(String email) {
        return usuarios.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + email + " no encontrado"));
    }
}
