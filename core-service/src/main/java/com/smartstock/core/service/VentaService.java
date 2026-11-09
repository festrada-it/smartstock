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
import java.util.TreeMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstock.core.dto.FaltanteStock;
import com.smartstock.core.dto.VentaRequest;
import com.smartstock.core.dto.VentaResponse;
import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.exception.RecursoNoEncontradoException;
import com.smartstock.core.exception.StockInsuficienteException;
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
import com.smartstock.core.model.VentaPerdida;
import com.smartstock.core.model.Cliente;
import com.smartstock.core.repository.ClienteRepository;
import com.smartstock.core.repository.MovimientoStockRepository;
import com.smartstock.core.repository.ProductoRepository;
import com.smartstock.core.repository.UsuarioRepository;
import com.smartstock.core.repository.VentaPerdidaRepository;
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
    private final VentaPerdidaRepository ventasPerdidas;

    public VentaService(VentaRepository ventaRepository, ProductoRepository productoRepository,
                        UsuarioRepository usuarioRepository, MovimientoStockRepository movimientoRepository,
                        ClienteRepository clienteRepository, JdbcTemplate db, LoteService loteService,
                        LoteRepository lotes, ConsumoLoteRepository consumos,
                        VentaPerdidaRepository ventasPerdidas) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.clienteRepository = clienteRepository;
        this.db = db;
        this.loteService = loteService;
        this.lotes = lotes;
        this.consumos = consumos;
        this.ventasPerdidas = ventasPerdidas;
    }

    /**
     * Registra una venta de forma atómica: crea la venta y su detalle, descuenta el stock y registra
     * los movimientos. Si falta stock no crea nada y guarda la venta perdida de las líneas sin stock.
     */
    @Transactional(noRollbackFor = StockInsuficienteException.class)
    public VentaResponse registrar(VentaRequest request, String emailVendedor) {
        Usuario vendedor = usuarioRepository.findByEmail(emailVendedor)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + emailVendedor + " no encontrado"));

        Venta venta = new Venta();
        venta.setUsuario(vendedor);
        venta.setCliente(request.clienteId() == null ? consumidorFinal()
                : clienteRepository.findById(request.clienteId()).filter(Cliente::isActivo)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Cliente " + request.clienteId() + " no encontrado")));
        BigDecimal total = BigDecimal.ZERO;
        var pedidas = new TreeMap<Long, Integer>();
        // Todas las operaciones de varios productos toman los bloqueos en el mismo orden.
        Map<Long, Producto> productos = bloquearProductos(request.items().stream()
                .map(VentaRequest.Item::productoId).toList());
        for (VentaRequest.Item item : request.items()) {
            Producto producto = productos.get(item.productoId());
            DetalleVenta detalle = new DetalleVenta(producto, item.cantidad(), producto.getPrecio());
            venta.agregarDetalle(detalle);
            total = total.add(detalle.getSubtotal());
            try {
                pedidas.merge(item.productoId(), item.cantidad(), Math::addExact);
            } catch (ArithmeticException e) {
                throw new NegocioException("La cantidad del producto supera el máximo permitido");
            }
        }

        if (total.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new NegocioException("El importe de la venta supera el máximo permitido");
        }
        var vendibles = lotesVendibles(productos, pedidas, vendedor, venta.getCliente());
        asignarNumeroOrden(venta);
        venta.setSubtotal(total.setScale(2, RoundingMode.HALF_UP));
        venta.setTotal(total.setScale(2, RoundingMode.HALF_UP));
        // IDENTITY persiste también los detalles antes de insertar sus consumos (FK).
        Venta guardada = ventaRepository.save(venta);
        var movimientos = descontarStock(guardada, vendedor, productos, vendibles);
        movimientoRepository.saveAll(movimientos);
        return VentaResponse.desde(guardada);
    }

    @Transactional(readOnly = true)
    public List<VentaResponse> ventasDelDia() {
        return ventaRepository.findDesdeConDetalles(LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).atStartOfDay()).stream()
                .map(VentaResponse::desde)
                .toList();
    }

    /** Alias temporal: comparte la cancelación y conserva el DTO legacy. */
    @Transactional
    public VentaResponse anular(Long id, String emailUsuario) {
        return VentaResponse.desde(cancelar(id, emailUsuario));
    }

    Venta cancelar(Long id, String emailUsuario) {
        Venta venta = ventaRepository.findByIdParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido " + id + " no encontrado"));
        if (venta.getEstado() == EstadoPedido.CANCELADO) {
            throw new NegocioException("El pedido " + id + " ya está cancelado");
        }
        if (venta.getEstado() != EstadoPedido.PENDIENTE && venta.getEstado() != EstadoPedido.CONFIRMADO) {
            throw new NegocioException("Solo se puede cancelar un pedido PENDIENTE o CONFIRMADO");
        }
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + emailUsuario + " no encontrado"));
        Map<Long, Producto> productos = bloquearProductos(venta.getDetalles().stream()
                .map(d -> d.getProducto().getId()).toList());
        var movimientos = restituirStock(venta, usuario, productos);
        movimientos.forEach(m -> m.setObservacion("Cancelación de pedido " + venta.getNumeroOrden()));
        venta.setEstado(EstadoPedido.CANCELADO);
        movimientoRepository.saveAll(movimientos);
        return venta;
    }

    /**
     * Valida todas las líneas antes de escribir el pedido y devuelve los lotes vigentes en orden FEFO.
     * Si alguna no alcanza, guarda la venta perdida de esas líneas y rechaza: quien llama tiene que
     * declarar StockInsuficienteException en noRollbackFor y no haber persistido nada del pedido.
     */
    Map<Long, List<Lote>> lotesVendibles(Map<Long, Producto> productos, Map<Long, Integer> pedidas,
                                         Usuario usuario, Cliente cliente) {
        LocalDate hoy = LoteService.hoy();
        Map<Long, List<Lote>> vendibles = new HashMap<>();
        var perdidas = new ArrayList<VentaPerdida>();
        var faltantes = new ArrayList<FaltanteStock>();
        for (var pedida : new TreeMap<>(pedidas).entrySet()) {
            Producto producto = productos.get(pedida.getKey());
            if (!Boolean.TRUE.equals(producto.getActivo())) {
                throw new RecursoNoEncontradoException("Producto " + producto.getId() + " no encontrado");
            }
            loteService.asegurarApertura(producto);
            List<Lote> conDisponible = lotes.bloquearDisponiblesFefo(producto.getId());
            if (conDisponible.stream().mapToLong(Lote::getCantidadDisponible).sum() != producto.getStockActual()) {
                throw new NegocioException("Los lotes de " + producto.getNombre() + " no coinciden con su stock");
            }
            List<Lote> vigentes = conDisponible.stream().filter(lote -> !lote.estaVencido(hoy)).toList();
            vendibles.put(producto.getId(), vigentes);
            long disponible = vigentes.stream().mapToLong(Lote::getCantidadDisponible).sum();
            if (pedida.getValue() > disponible) {
                perdidas.add(new VentaPerdida(cliente, usuario, producto, pedida.getValue(), (int) disponible));
                faltantes.add(new FaltanteStock(producto.getId(), producto.getNombre(), pedida.getValue(),
                        (int) disponible, pedida.getValue() - (int) disponible));
            }
        }
        if (!faltantes.isEmpty()) {
            ventasPerdidas.saveAll(perdidas);
            throw new StockInsuficienteException(faltantes);
        }
        return vendibles;
    }

    /** Consume en orden FEFO los lotes ya validados por lotesVendibles. */
    List<MovimientoStock> descontarStock(Venta venta, Usuario usuario, Map<Long, Producto> productos,
                                         Map<Long, List<Lote>> vendibles) {
        var movimientos = new ArrayList<MovimientoStock>();
        for (var detalle : venta.getDetalles()) {
            var producto = productos.get(detalle.getProducto().getId());
            int restante = detalle.getCantidad();
            for (var lote : vendibles.get(producto.getId())) {
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
        bloquearLotes(productos);
        var movimientos = new ArrayList<MovimientoStock>();
        for (var detalle : venta.getDetalles()) {
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
                movimientos.add(movimiento);
            } else {
                if (originales.stream().mapToLong(ConsumoLote::getCantidad).sum() != detalle.getCantidad()) {
                    throw new NegocioException("Los consumos de " + producto.getNombre() + " no coinciden con la cantidad vendida");
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
                    movimientos.add(movimiento);
                }
            }
        }
        return movimientos;
    }

    private void bloquearLotes(Map<Long, Producto> productos) {
        productos.keySet().stream().sorted().forEach(id -> {
            loteService.asegurarApertura(productos.get(id));
            lotes.bloquearPorProducto(id);
        });
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
