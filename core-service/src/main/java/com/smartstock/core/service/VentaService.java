package com.smartstock.core.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    public VentaService(VentaRepository ventaRepository, ProductoRepository productoRepository,
                        UsuarioRepository usuarioRepository, MovimientoStockRepository movimientoRepository) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoRepository = movimientoRepository;
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
        BigDecimal total = BigDecimal.ZERO;
        List<MovimientoStock> movimientos = new ArrayList<>();

        for (VentaRequest.Item item : request.items()) {
            Producto producto = productoRepository.findByIdParaActualizar(item.productoId())
                    .filter(Producto::getActivo)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + item.productoId() + " no encontrado"));

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
}
