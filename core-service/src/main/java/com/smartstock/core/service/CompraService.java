package com.smartstock.core.service;

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

    public CompraService(ProductoRepository productoRepository, UsuarioRepository usuarioRepository,
                         MovimientoStockRepository movimientoRepository) {
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoRepository = movimientoRepository;
    }

    @Transactional
    public ProductoResponse registrar(CompraRequest request, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + emailUsuario + " no encontrado"));
        Producto producto = productoRepository.findByIdParaActualizar(request.productoId())
                .filter(Producto::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + request.productoId() + " no encontrado"));

        producto.setStockActual(producto.getStockActual() + request.cantidad());
        if (request.costoUnitario() != null) {
            producto.setCosto(request.costoUnitario());
        }

        MovimientoStock movimiento = new MovimientoStock(producto, TipoMovimiento.COMPRA, request.cantidad(), usuario);
        movimiento.setObservacion(request.observacion());
        movimientoRepository.save(movimiento);

        return ProductoResponse.desde(producto);
    }
}
