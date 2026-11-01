package com.smartstock.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smartstock.core.exception.RecursoNoEncontradoException;
import com.smartstock.core.model.*;
import com.smartstock.core.repository.*;

@ExtendWith(MockitoExtension.class)
class MovimientoServiceTest {
    @Mock ProductoRepository productos;
    @Mock CategoriaRepository categorias;
    @Mock MovimientoStockRepository movimientos;
    @InjectMocks ProductoService servicio;

    @Test
    void productoInexistenteDevuelveErrorYNoConsultaMovimientos() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.movimientos(99L, 0, 50));
        verifyNoInteractions(movimientos);
    }

    @Test
    void productoSinMovimientosDevuelveListaVacia() {
        when(productos.existsById(1L)).thenReturn(true);
        when(movimientos.findHistorial(1L, PageRequest.of(0, 50))).thenReturn(new PageImpl<>(List.of()));
        assertTrue(servicio.movimientos(1L, 0, 50).contenido().isEmpty());
    }

    @Test
    void conservaHistorialDeInactivosYDatosDeAuditoriaSinExponerEntidades() {
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setActivo(false);
        Usuario usuario = new Usuario();
        usuario.setId(2L);
        usuario.setNombre("Nicolás");
        Venta venta = new Venta();
        venta.setId(3L);
        MovimientoStock salida = new MovimientoStock(producto, TipoMovimiento.VENTA, -2, usuario);
        salida.setVenta(venta);
        salida.setObservacion("Venta de prueba");
        MovimientoStock entrada = new MovimientoStock(producto, TipoMovimiento.COMPRA, 5, null);
        when(productos.existsById(1L)).thenReturn(true);
        when(movimientos.findHistorial(1L, PageRequest.of(0, 50))).thenReturn(new PageImpl<>(List.of(salida, entrada)));

        var historial = servicio.movimientos(1L, 0, 50).contenido();

        assertEquals(2, historial.size());
        assertEquals(-2, historial.getFirst().cantidad());
        assertEquals(TipoMovimiento.VENTA, historial.getFirst().tipo());
        assertEquals(1L, historial.getFirst().productoId());
        assertEquals(2L, historial.getFirst().usuarioId());
        assertEquals("Nicolás", historial.getFirst().usuario());
        assertEquals(3L, historial.getFirst().ventaId());
        assertEquals("Venta de prueba", historial.getFirst().observacion());
        assertNotNull(historial.getFirst().fecha());
        assertNull(historial.get(1).usuario());
        assertNull(historial.get(1).ventaId());
    }
}
