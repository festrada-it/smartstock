package com.smartstock.core.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MovimientoStockRepository movimientoRepository;

    public ProductoService(ProductoRepository productoRepository, CategoriaRepository categoriaRepository,
                           MovimientoStockRepository movimientoRepository) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.movimientoRepository = movimientoRepository;
    }

    /** Conserva el historial incluso después de la baja lógica del producto. */
    @Transactional(readOnly = true)
    public List<MovimientoStockResponse> movimientos(Long id) {
        if (!productoRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Producto " + id + " no encontrado");
        }
        return movimientoRepository.findHistorial(id).stream()
                .map(MovimientoStockResponse::desde).toList();
    }

    /** Lista los productos activos, filtrando opcionalmente por texto (nombre o SKU) y categoría. */
    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(String busqueda, Long categoriaId) {
        String texto = busqueda == null ? "" : busqueda.trim().toLowerCase(Locale.ROOT);
        return productoRepository.findActivosConCategoria().stream()
                .filter(p -> texto.isEmpty()
                        || p.getNombre().toLowerCase(Locale.ROOT).contains(texto)
                        || p.getSku().toLowerCase(Locale.ROOT).contains(texto))
                .filter(p -> categoriaId == null
                        || (p.getCategoria() != null && categoriaId.equals(p.getCategoria().getId())))
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
        return ProductoResponse.desde(productoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest request) {
        Producto producto = buscarActivo(id);
        if (productoRepository.existsBySkuAndIdNot(request.sku(), id)) {
            throw new NegocioException("Ya existe otro producto con SKU " + request.sku());
        }
        aplicar(request, producto);
        return ProductoResponse.desde(producto);
    }

    /** Baja lógica: el producto se desactiva pero se conserva su historial de ventas. */
    @Transactional
    public void eliminar(Long id) {
        buscarActivo(id).setActivo(false);
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
    }

    private Categoria buscarCategoria(Long categoriaId) {
        if (categoriaId == null) {
            return null;
        }
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría " + categoriaId + " no encontrada"));
    }
}
