package com.smartstock.core.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstock.core.dto.VentaRequest;
import com.smartstock.core.dto.VentaResponse;
import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.exception.RecursoNoEncontradoException;
import com.smartstock.core.model.DetalleVenta;
import com.smartstock.core.model.MovimientoStock;
import com.smartstock.core.model.Producto;
import com.smartstock.core.model.TipoMovimiento;
import com.smartstock.core.model.Usuario;
import com.smartstock.core.model.Venta;
import com.smartstock.core.model.Cliente;
import com.smartstock.core.repository.ClienteRepository;
import com.smartstock.core.repository.MovimientoStockRepository;
import com.smartstock.core.repository.ProductoRepository;
import com.smartstock.core.repository.UsuarioRepository;
import com.smartstock.core.repository.VentaRepository;

@Service
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoStockRepository movimientoRepository;
    private final ClienteRepository clienteRepository;

    public VentaService(VentaRepository ventaRepository, ProductoRepository productoRepository,
                        UsuarioRepository usuarioRepository, MovimientoStockRepository movimientoRepository,ClienteRepository clienteRepository) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.clienteRepository=clienteRepository;
    }

    /**
     * Registra una venta de forma atómica: crea la venta y su detalle, descuenta el stock
     * y registra los movimientos. Si algún producto no tiene stock suficiente, no se guarda nada (rollback).
     */
    @Transactional
    public VentaResponse registrar(VentaRequest request, String emailVendedor) {
        Usuario vendedor = usuarioRepository.findByEmail(emailVendedor)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + emailVendedor + " no encontrado"));

        Venta venta = new Venta();
        venta.setUsuario(vendedor);
        if(request.clienteId()!=null) {
            venta.setCliente(clienteRepository.findById(request.clienteId()).filter(Cliente::isActivo)
                    .orElseThrow(()->new RecursoNoEncontradoException("Cliente "+request.clienteId()+" no encontrado")));
        }
        BigDecimal total = BigDecimal.ZERO;
        List<MovimientoStock> movimientos = new ArrayList<>();

        // Todas las operaciones de varios productos toman los bloqueos en el mismo orden.
        Map<Long, Producto> productos = bloquearProductos(request.items().stream()
                .map(VentaRequest.Item::productoId).toList());
        for (VentaRequest.Item item : request.items()) {
            Producto producto = productos.get(item.productoId());
            if (!producto.getActivo()) {
                throw new RecursoNoEncontradoException("Producto " + item.productoId() + " no encontrado");
            }

            if (producto.getStockActual() < item.cantidad()) {
                throw new NegocioException("Stock insuficiente para " + producto.getNombre()
                        + " (disponible: " + producto.getStockActual() + ", pedido: " + item.cantidad() + ")");
            }
            producto.setStockActual(producto.getStockActual() - item.cantidad());

            DetalleVenta detalle = new DetalleVenta(producto, item.cantidad(), producto.getPrecio());
            venta.agregarDetalle(detalle);
            total = total.add(detalle.getSubtotal());
            movimientos.add(new MovimientoStock(producto, TipoMovimiento.VENTA, -item.cantidad(), vendedor));
        }

        venta.setTotal(total);
        Venta guardada = ventaRepository.save(venta);
        movimientos.forEach(m -> m.setVenta(guardada));
        movimientoRepository.saveAll(movimientos);
        return VentaResponse.desde(guardada);
    }

    @Transactional(readOnly = true)
    public List<VentaResponse> ventasDelDia() {
        return ventaRepository.findDesdeConDetalles(LocalDate.now().atStartOfDay()).stream()
                .map(VentaResponse::desde)
                .toList();
    }

    /** La venta permanece para auditoría; el bloqueo evita devolver el stock dos veces. */
    @Transactional
    public VentaResponse anular(Long id, String emailUsuario) {
        Venta venta = ventaRepository.findByIdParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta " + id + " no encontrada"));
        if (venta.isAnulada()) {
            throw new NegocioException("La venta " + id + " ya está anulada");
        }
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + emailUsuario + " no encontrado"));
        Map<Long, Producto> productos = bloquearProductos(venta.getDetalles().stream()
                .map(d -> d.getProducto().getId()).toList());
        List<MovimientoStock> movimientos = new ArrayList<>();
        for (DetalleVenta detalle : venta.getDetalles()) {
            Producto producto = productos.get(detalle.getProducto().getId());
            try {
                producto.setStockActual(Math.addExact(producto.getStockActual(), detalle.getCantidad()));
            } catch (ArithmeticException e) {
                throw new NegocioException("El stock de " + producto.getNombre() + " supera el máximo permitido");
            }
            MovimientoStock movimiento = new MovimientoStock(producto, TipoMovimiento.AJUSTE,
                    detalle.getCantidad(), usuario);
            movimiento.setVenta(venta);
            movimiento.setObservacion("Anulación de venta " + id);
            movimientos.add(movimiento);
        }
        venta.setAnulada(true);
        movimientoRepository.saveAll(movimientos);
        return VentaResponse.desde(venta);
    }

    private Map<Long, Producto> bloquearProductos(List<Long> ids) {
        Map<Long, Producto> productos = new HashMap<>();
        ids.stream().distinct().sorted(Comparator.naturalOrder()).forEach(id ->
                productos.put(id, productoRepository.findByIdParaActualizar(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no encontrado"))));
        return productos;
    }
}
