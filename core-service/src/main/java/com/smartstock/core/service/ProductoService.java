package com.smartstock.core.service;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstock.core.dto.HistorialMovimientosResponse;
import com.smartstock.core.dto.ProductoRequest;
import com.smartstock.core.dto.MovimientoStockResponse;
import com.smartstock.core.dto.ProductoResponse;
import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.exception.RecursoNoEncontradoException;
import com.smartstock.core.model.Categoria;
import com.smartstock.core.model.Producto;
import com.smartstock.core.repository.CategoriaRepository;
import com.smartstock.core.repository.ProductoRepository;
import com.smartstock.core.repository.MovimientoStockRepository;
import com.smartstock.core.repository.ProveedorRepository;
import com.smartstock.core.model.Proveedor;
import com.smartstock.core.model.MovimientoStock;
import com.smartstock.core.model.TipoMovimiento;
import com.smartstock.core.dto.LoteResponse;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MovimientoStockRepository movimientoRepository;
    private final ProveedorRepository proveedorRepository;

    private final LoteService loteService;

    public ProductoService(ProductoRepository productoRepository, CategoriaRepository categoriaRepository,
                           MovimientoStockRepository movimientoRepository,ProveedorRepository proveedorRepository, LoteService loteService) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.movimientoRepository = movimientoRepository;
        this.proveedorRepository=proveedorRepository;
        this.loteService = loteService;
    }

    /** Conserva el historial incluso después de la baja lógica del producto. */
    @Transactional(readOnly = true)
    public HistorialMovimientosResponse movimientos(Long id, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new NegocioException("La página debe ser válida y el tamaño debe estar entre 1 y 100");
        }
        if (!productoRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Producto " + id + " no encontrado");
        }
        var historial = movimientoRepository.findHistorial(id, PageRequest.of(page, size));
        return new HistorialMovimientosResponse(
                historial.getContent().stream().map(MovimientoStockResponse::desde).toList(),
                page, size, historial.getTotalElements(), historial.getTotalPages());
    }

    /** Lista los productos activos, filtrando opcionalmente por texto (nombre o SKU) y categoría. */
    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(String busqueda, Long categoriaId,Long proveedorId) {
        String texto = busqueda == null ? "" : busqueda.trim().toLowerCase(Locale.ROOT);
        return productoRepository.findActivosConCategoria().stream()
                .filter(p -> texto.isEmpty()
                        || p.getNombre().toLowerCase(Locale.ROOT).contains(texto)
                        || p.getSku().toLowerCase(Locale.ROOT).contains(texto))
                .filter(p -> categoriaId == null
                        || (p.getCategoria() != null && categoriaId.equals(p.getCategoria().getId())))
                .filter(p -> proveedorId==null || (p.getProveedor()!=null && proveedorId.equals(p.getProveedor().getId())))
                .map(ProductoResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return ProductoResponse.desde(buscarActivo(id));
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest request) {
        if (productoRepository.existsBySku(request.sku())) {
            throw new NegocioException("Ya existe un producto con SKU " + request.sku());
        }
        Producto producto = new Producto();
        aplicar(request, producto);
        productoRepository.save(producto);
        loteService.asegurarApertura(producto);
        return ProductoResponse.desde(producto);
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest request) {
        Producto producto = buscarActivoParaActualizar(id);
        int anterior = producto.getStockActual();
        loteService.asegurarApertura(producto);
        if (productoRepository.existsBySkuAndIdNot(request.sku(), id)) {
            throw new NegocioException("Ya existe otro producto con SKU " + request.sku());
        }
        aplicar(request, producto);
        int diferencia = producto.getStockActual() - anterior;
        loteService.ajustarDisponible(producto, diferencia);
        if (diferencia != 0) {
            MovimientoStock movimiento = new MovimientoStock(producto, TipoMovimiento.AJUSTE, diferencia, null);
            movimiento.setObservacion("Ajuste de stock desde catálogo");
            movimientoRepository.save(movimiento);
        }
        return ProductoResponse.desde(producto);
    }

    /** Baja lógica: el producto se desactiva pero se conserva su historial de ventas. */
    @Transactional
    public void eliminar(Long id) {
        buscarActivoParaActualizar(id).setActivo(false);
    }

    @Transactional(readOnly = true)
    public List<LoteResponse> lotes(Long id, boolean todos) {
        return loteService.consultar(id, todos);
    }

    private Producto buscarActivoParaActualizar(Long id) {
        return productoRepository.findByIdParaActualizar(id).filter(Producto::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no encontrado"));
    }

    private Producto buscarActivo(Long id) {
        return productoRepository.findById(id)
                .filter(Producto::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no encontrado"));
    }

    private void aplicar(ProductoRequest request, Producto producto) {
        producto.setSku(request.sku().trim());
        producto.setNombre(request.nombre().trim());
        producto.setCategoria(buscarCategoria(request.categoriaId()));
        producto.setPrecio(request.precio());
        producto.setCosto(request.costo());
        producto.setStockActual(request.stockActual());
        producto.setStockMinimo(request.stockMinimo());
        // El catálogo anterior omite estos campos al editar: conservar sus datos.
        if (request.marca() != null) {
            producto.setMarca(request.marca().isBlank() ? null : request.marca().trim());
        }
        if (request.proveedorId() != null) {
            producto.setProveedor(proveedorRepository.findById(request.proveedorId())
                    .filter(Proveedor::isActivo)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Proveedor " + request.proveedorId() + " no encontrado")));
        }
    }

    private Categoria buscarCategoria(Long categoriaId) {
        if (categoriaId == null) {
            return null;
        }
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría " + categoriaId + " no encontrada"));
    }
}
