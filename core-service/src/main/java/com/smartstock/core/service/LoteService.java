package com.smartstock.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartstock.core.dto.LoteResponse;
import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.exception.RecursoNoEncontradoException;
import com.smartstock.core.model.*;
import com.smartstock.core.repository.*;

@Service
public class LoteService {
    private final LoteRepository lotes;
    private final ProductoRepository productos;
    public LoteService(LoteRepository lotes, ProductoRepository productos) {
        this.lotes = lotes;
        this.productos = productos;
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

    /** shortcut: puente legacy por id; #21 reemplaza por FEFO y registra los consumos originales. */
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
