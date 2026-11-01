package com.smartstock.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstock.core.dto.VentaRequest;
import com.smartstock.core.dto.VentaResponse;
import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.exception.RecursoNoEncontradoException;
import com.smartstock.core.model.Calidad;
import com.smartstock.core.model.ConsumoLote;
import com.smartstock.core.model.Lote;
import com.smartstock.core.repository.LoteRepository;
import com.smartstock.core.repository.ConsumoLoteRepository;
import com.smartstock.core.model.EstadoPedido;
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

    private final JdbcTemplate db;
    private final LoteService loteService;
    private final LoteRepository lotes;
    private final ConsumoLoteRepository consumos;

    public VentaService(VentaRepository ventaRepository, ProductoRepository productoRepository,
                        UsuarioRepository usuarioRepository, MovimientoStockRepository movimientoRepository,
                        ClienteRepository clienteRepository, JdbcTemplate db, LoteService loteService,
                        LoteRepository lotes, ConsumoLoteRepository consumos) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.clienteRepository = clienteRepository;
        this.db = db;
        this.loteService = loteService;
        this.lotes = lotes;
        this.consumos = consumos;
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
        venta.setCliente(request.clienteId() == null ? consumidorFinal()
                : clienteRepository.findById(request.clienteId()).filter(Cliente::isActivo)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Cliente " + request.clienteId() + " no encontrado")));
        asignarNumeroOrden(venta);
        BigDecimal total = BigDecimal.ZERO;
        // Todas las operaciones de varios productos toman los bloqueos en el mismo orden.
        Map<Long, Producto> productos = bloquearProductos(request.items().stream()
                .map(VentaRequest.Item::productoId).toList());
        for (VentaRequest.Item item : request.items()) {
            Producto producto = productos.get(item.productoId());
            DetalleVenta detalle = new DetalleVenta(producto, item.cantidad(), producto.getPrecio());
            venta.agregarDetalle(detalle);
            total = total.add(detalle.getSubtotal());
        }

        if (total.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new NegocioException("El importe de la venta supera el máximo permitido");
        }
        venta.setSubtotal(total.setScale(2, RoundingMode.HALF_UP));
        venta.setTotal(total.setScale(2, RoundingMode.HALF_UP));
        // IDENTITY persiste también los detalles antes de insertar sus consumos (FK).
        Venta guardada = ventaRepository.save(venta);
        var movimientos = descontarStock(guardada, vendedor, productos);
        movimientoRepository.saveAll(movimientos);
        return VentaResponse.desde(guardada);
    }

    @Transactional(readOnly = true)
    public List<VentaResponse> ventasDelDia() {
        return ventaRepository.findDesdeConDetalles(LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).atStartOfDay()).stream()
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
        if (venta.getEstado() != EstadoPedido.CONFIRMADO) {
            throw new NegocioException("Solo se puede anular una venta CONFIRMADA");
        }
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + emailUsuario + " no encontrado"));
        Map<Long, Producto> productos = bloquearProductos(venta.getDetalles().stream()
                .map(d -> d.getProducto().getId()).toList());
        var movimientos = restituirStock(venta, usuario, productos);
        venta.setEstado(EstadoPedido.CANCELADO);
        movimientoRepository.saveAll(movimientos);
        return VentaResponse.desde(venta);
    }

    List<MovimientoStock> descontarStock(Venta venta, Usuario usuario, Map<Long, Producto> productos) {
        var disponibles = bloquearLotes(productos, true);
        var movimientos = new ArrayList<MovimientoStock>();
        for (var detalle : venta.getDetalles()) {
            if (detalle.getCantidad() == 0) continue;
            var producto = productos.get(detalle.getProducto().getId());
            if (!Boolean.TRUE.equals(producto.getActivo())) {
                throw new RecursoNoEncontradoException("Producto " + producto.getId() + " no encontrado");
            }
            if (producto.getStockActual() < detalle.getCantidad()) {
                throw new NegocioException("Stock insuficiente para " + producto.getNombre()
                        + " (disponible: " + producto.getStockActual() + ", pedido: " + detalle.getCantidad() + ")");
            }
            int restante = detalle.getCantidad();
            for (var lote : disponibles.get(producto.getId())) {
                int cantidad = Math.min(restante, lote.getCantidadDisponible());
                if (cantidad == 0) continue;
                lote.setCantidadDisponible(lote.getCantidadDisponible() - cantidad);
                consumos.save(new ConsumoLote(detalle, lote, cantidad));
                var movimiento = new MovimientoStock(producto, TipoMovimiento.VENTA, -cantidad, usuario);
                movimiento.setVenta(venta);
                movimiento.setLoteId(lote.getId());
                movimientos.add(movimiento);
                restante -= cantidad;
                if (restante == 0) break;
            }
            if (restante != 0) throw new NegocioException("Los lotes de " + producto.getNombre() + " no coinciden con su stock");
            producto.setStockActual(producto.getStockActual() - detalle.getCantidad());
        }
        return movimientos;
    }

    /** El llamador bloquea la venta y todos sus productos, y controla la transición de estado. */
    List<MovimientoStock> restituirStock(Venta venta, Usuario usuario, Map<Long, Producto> productos) {
        bloquearLotes(productos, false);
        var movimientos = new ArrayList<MovimientoStock>();
        for (var detalle : venta.getDetalles()) {
            if (detalle.getCantidad() == 0) continue;
            var producto = productos.get(detalle.getProducto().getId());
            try {
                producto.setStockActual(Math.addExact(producto.getStockActual(), detalle.getCantidad()));
            } catch (ArithmeticException e) {
                throw new NegocioException("El stock de " + producto.getNombre() + " supera el máximo permitido");
            }
            var originales = consumos.findByDetalleVentaIdOrderByIdAsc(detalle.getId());
            if (originales.isEmpty()) {
                // Ventas históricas sin consumos: el origen no se puede reconstruir.
                loteService.ajustarDisponible(producto, detalle.getCantidad());
                var apertura = producto.getLotes().stream().filter(Lote::isEsApertura).findFirst().orElseThrow();
                var movimiento = new MovimientoStock(producto, TipoMovimiento.AJUSTE, detalle.getCantidad(), usuario);
                movimiento.setVenta(venta);
                movimiento.setLoteId(apertura.getId());
                movimiento.setObservacion("Anulación de venta " + venta.getId());
                movimientos.add(movimiento);
            } else {
                if (originales.stream().mapToLong(ConsumoLote::getCantidad).sum() != detalle.getCantidad()) {
                    throw new NegocioException("Los consumos de " + producto.getNombre() + " no coinciden con la cantidad entregada");
                }
                for (var consumo : originales) {
                    var lote = consumo.getLote();
                    try {
                        lote.setCantidadDisponible(Math.addExact(lote.getCantidadDisponible(), consumo.getCantidad()));
                    } catch (ArithmeticException e) {
                        throw new NegocioException("El disponible del lote " + lote.getId() + " supera el máximo permitido");
                    }
                    var movimiento = new MovimientoStock(producto, TipoMovimiento.AJUSTE, consumo.getCantidad(), usuario);
                    movimiento.setVenta(venta);
                    movimiento.setLoteId(lote.getId());
                    movimiento.setObservacion("Anulación de venta " + venta.getId());
                    movimientos.add(movimiento);
                }
            }
        }
        return movimientos;
    }

    private Map<Long, List<Lote>> bloquearLotes(Map<Long, Producto> productos, boolean soloDisponibles) {
        Map<Long, List<Lote>> bloqueados = new HashMap<>();
        productos.keySet().stream().sorted().forEach(id -> {
            loteService.asegurarApertura(productos.get(id));
            bloqueados.put(id, soloDisponibles ? lotes.bloquearDisponiblesFefo(id) : lotes.bloquearPorProducto(id));
        });
        return bloqueados;
    }

    void asignarNumeroOrden(Venta venta) {
        venta.setNumeroOrden(String.format(Locale.ROOT, "OC-%04d-%06d", venta.getFecha().getYear(),
                ventaRepository.siguienteNumeroOrden()));
    }

    private Cliente consumidorFinal() {
        var existente = clienteRepository.findFirstByNombreAndActivoTrueOrderByIdAsc("Consumidor final");
        if (existente.isPresent()) return existente.get();
        // El lock transaccional evita duplicar el fallback entre varias instancias del core.
        db.execute("SELECT pg_advisory_xact_lock(24, 1)");
        return clienteRepository.findFirstByNombreAndActivoTrueOrderByIdAsc("Consumidor final")
                .orElseGet(() -> {
                    var cliente = new Cliente();
                    cliente.setNombre("Consumidor final");
                    cliente.setCalidad(Calidad.REGULAR);
                    cliente.setClasificacion(3);
                    return clienteRepository.save(cliente);
                });
    }

    Map<Long, Producto> bloquearProductos(List<Long> ids) {
        Map<Long, Producto> productos = new HashMap<>();
        ids.stream().distinct().sorted(Comparator.naturalOrder()).forEach(id ->
                productos.put(id, productoRepository.findByIdParaActualizar(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no encontrado"))));
        return productos;
    }
}
