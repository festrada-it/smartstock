package com.smartstock.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartstock.core.dto.LoteResponse;
import com.smartstock.core.dto.RetiroLoteResponse;
import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.exception.RecursoNoEncontradoException;
import com.smartstock.core.model.*;
import com.smartstock.core.repository.*;

@Service
public class LoteService {
    static final String OBSERVACION_RETIRO = "Retiro de lote vencido";

    private final LoteRepository lotes;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;
    private final MovimientoStockRepository movimientos;
    public LoteService(LoteRepository lotes, ProductoRepository productos,
                       UsuarioRepository usuarios, MovimientoStockRepository movimientos) {
        this.lotes = lotes;
        this.productos = productos;
        this.usuarios = usuarios;
        this.movimientos = movimientos;
    }
    public static LocalDate hoy() { return LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")); }
    public static BigDecimal normalizarCosto(BigDecimal costo) {
        BigDecimal normalizado = costo.setScale(2, RoundingMode.HALF_UP);
        if (costo.signum() < 0 || normalizado.precision() > 12) {
            throw new NegocioException("El costo debe estar entre 0 y 9999999999,99");
        }
        return normalizado;
    }

    @Transactional(readOnly = true)
    public List<LoteResponse> consultar(Long id, boolean todos) {
        if (!productos.existsById(id)) throw new RecursoNoEncontradoException("Producto " + id + " no encontrado");
        return lotes.consultar(id, todos).stream().map(LoteResponse::desde).toList();
    }

    /**
     * Retira el lote vencido completo: lo deja en 0, descuenta el stock y registra un AJUSTE negativo.
     * Bloquea el producto antes que el lote, el mismo orden que usan ventas y compras.
     */
    @Transactional
    public RetiroLoteResponse retirarVencido(Long loteId, String observacion, String email) {
        Long productoId = lotes.productoDe(loteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lote " + loteId + " no encontrado"));
        Producto producto = productos.findByIdParaActualizar(productoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lote " + loteId + " no encontrado"));
        Lote lote = lotes.bloquear(loteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lote " + loteId + " no encontrado"));
        if (!lote.estaVencido(hoy())) {
            throw new NegocioException("Solo se pueden retirar lotes vencidos");
        }
        int cantidad = lote.getCantidadDisponible();
        if (cantidad == 0) {
            throw new NegocioException("El lote " + loteId + " no tiene unidades para retirar");
        }
        Usuario usuario = usuarios.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + email + " no encontrado"));
        lote.setCantidadDisponible(0);
        producto.setStockActual(producto.getStockActual() - cantidad);
        var movimiento = new MovimientoStock(producto, TipoMovimiento.AJUSTE, -cantidad, usuario);
        movimiento.setLoteId(lote.getId());
        movimiento.setObservacion(observacion == null || observacion.isBlank()
                ? OBSERVACION_RETIRO : OBSERVACION_RETIRO + ": " + observacion.trim());
        movimientos.save(movimiento);
        return new RetiroLoteResponse(lote.getId(), producto.getId(), producto.getNombre(),
                lote.getFechaVencimiento(), cantidad, producto.getStockActual());
    }

    /** El llamador bloquea primero el producto; la apertura usa el stock ANTERIOR a la variación. */
    public void asegurarApertura(Producto producto) {
        if (producto.getLotes().isEmpty()) {
            guardar(producto, new Lote(producto, producto.getProveedor(), producto.getStockActual(),
                    null, hoy(), normalizarCosto(producto.getCosto()), true));
        }
    }
    public Lote ingresar(Producto producto, Proveedor proveedor, int cantidad, LocalDate vencimiento,
                         LocalDate entrega, BigDecimal costo) {
        asegurarApertura(producto);
        return guardar(producto, new Lote(producto, proveedor, cantidad, vencimiento, entrega, costo, false));
    }
    private Lote guardar(Producto producto, Lote lote) {
        lotes.save(lote);
        producto.getLotes().add(lote);
        return lote;
    }

    /** Ajustes del catálogo y restituciones históricas sin trazabilidad de consumo. */
    public void ajustarDisponible(Producto producto, int diferencia) {
        asegurarApertura(producto);
        List<Lote> disponibles = lotes.bloquearPorProducto(producto.getId());
        if (diferencia >= 0) {
            Lote apertura = disponibles.stream().filter(Lote::isEsApertura).findFirst()
                    .orElseGet(() -> guardar(producto, new Lote(producto, producto.getProveedor(), 0,
                            null, hoy(), normalizarCosto(producto.getCosto()), true)));
            try {
                apertura.setCantidadDisponible(Math.addExact(apertura.getCantidadDisponible(), diferencia));
            } catch (ArithmeticException e) {
                throw new NegocioException("El stock de " + producto.getNombre() + " supera el máximo permitido");
            }
        } else {
            int restante = -diferencia;
            for (Lote lote : disponibles) {
                int consumo = Math.min(restante, lote.getCantidadDisponible());
                lote.setCantidadDisponible(lote.getCantidadDisponible() - consumo);
                restante -= consumo;
                if (restante == 0) break;
            }
            if (restante != 0) throw new NegocioException("Los lotes de " + producto.getNombre() + " no coinciden con su stock");
        }
    }
}
