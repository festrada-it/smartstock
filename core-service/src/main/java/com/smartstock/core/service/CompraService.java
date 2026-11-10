package com.smartstock.core.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.smartstock.core.model.Proveedor;
import com.smartstock.core.model.Lote;
import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.repository.ProveedorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstock.core.dto.CompraRequest;
import com.smartstock.core.dto.ProductoResponse;
import com.smartstock.core.exception.RecursoNoEncontradoException;
import com.smartstock.core.model.MovimientoStock;
import com.smartstock.core.model.Producto;
import com.smartstock.core.model.TipoMovimiento;
import com.smartstock.core.model.Usuario;
import com.smartstock.core.repository.MovimientoStockRepository;
import com.smartstock.core.repository.ProductoRepository;
import com.smartstock.core.repository.UsuarioRepository;

/** Ingreso de compras / mercadería: suma stock y deja registrado el movimiento. */
@Service
public class CompraService {

    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoStockRepository movimientoRepository;

    private final LoteService loteService;
    private final ProveedorRepository proveedores;

    public CompraService(ProductoRepository productoRepository, UsuarioRepository usuarioRepository,
                         MovimientoStockRepository movimientoRepository, LoteService loteService, ProveedorRepository proveedores) {
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.loteService = loteService;
        this.proveedores = proveedores;
    }

    @Transactional
    public ProductoResponse registrar(CompraRequest request, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + emailUsuario + " no encontrado"));
        Producto producto = productoRepository.findByIdParaActualizar(request.productoId())
                .filter(Producto::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + request.productoId() + " no encontrado"));

        LocalDate hoy = LoteService.hoy();
        if (request.fechaVencimiento() != null && request.fechaVencimiento().isBefore(hoy)) {
            throw new NegocioException("La fecha de vencimiento no puede ser anterior a hoy");
        }
        int nuevoStock;
        try {
            nuevoStock = Math.addExact(producto.getStockActual(), request.cantidad());
        } catch (ArithmeticException e) {
            throw new NegocioException("El stock de " + producto.getNombre() + " supera el máximo permitido");
        }
        Long proveedorId = request.proveedorId() != null ? request.proveedorId()
                : producto.getProveedor() == null ? null : producto.getProveedor().getId();
        Proveedor proveedor = proveedorId == null ? null : proveedores.findById(proveedorId)
                .filter(Proveedor::isActivo).orElseThrow(() -> new RecursoNoEncontradoException(
                        "Proveedor " + proveedorId + " no encontrado"));
        BigDecimal costo = LoteService.normalizarCosto(request.costoUnitario() != null
                ? request.costoUnitario() : producto.getCosto());
        Lote lote = loteService.ingresar(producto, proveedor, request.cantidad(), request.fechaVencimiento(),
                request.fechaEntrega() == null ? hoy : request.fechaEntrega(), costo);
        producto.setStockActual(nuevoStock);
        if (request.costoUnitario() != null) producto.setCosto(costo);

        MovimientoStock movimiento = new MovimientoStock(producto, TipoMovimiento.COMPRA, request.cantidad(), usuario);
        movimiento.setObservacion(request.observacion());
        movimiento.setLoteId(lote.getId());
        movimientoRepository.save(movimiento);

        return ProductoResponse.desde(producto);
    }
}
