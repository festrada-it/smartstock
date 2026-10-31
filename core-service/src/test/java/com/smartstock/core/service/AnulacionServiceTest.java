package com.smartstock.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.exception.RecursoNoEncontradoException;
import com.smartstock.core.model.*;
import com.smartstock.core.repository.*;

@ExtendWith(MockitoExtension.class)
class AnulacionServiceTest {
    @Mock VentaRepository ventas;
    @Mock ProductoRepository productos;
    @Mock UsuarioRepository usuarios;
    @Mock MovimientoStockRepository movimientos;
    @InjectMocks VentaService servicio;

    private Venta venta;
    private Usuario admin;
    private Producto producto;

    @BeforeEach
    void preparar() {
        admin = new Usuario();
        admin.setNombre("Admin");
        venta = new Venta();
        venta.setId(7L);
        venta.setUsuario(admin);
        venta.setTotal(new BigDecimal("20.00"));
        producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Yerba");
        producto.setStockActual(8);
        venta.agregarDetalle(new DetalleVenta(producto, 2, new BigDecimal("10.00")));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void devuelveStockConservaVentaYRegistraQuienAnula() {
        when(ventas.findByIdParaActualizar(7L)).thenReturn(Optional.of(venta));
        when(usuarios.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(productos.findByIdParaActualizar(1L)).thenReturn(Optional.of(producto));

        var respuesta = servicio.anular(7L, "admin@example.com");

        assertEquals(10, producto.getStockActual());
        assertTrue(venta.isAnulada());
        assertTrue(respuesta.anulada());
        assertEquals(new BigDecimal("20.00"), respuesta.total());
        assertEquals(1, respuesta.items().size());
        ArgumentCaptor<List<MovimientoStock>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(movimientos).saveAll(captor.capture());
        var movimiento = captor.getValue().getFirst();
        assertEquals(2, movimiento.getCantidad());
        assertEquals(TipoMovimiento.AJUSTE, movimiento.getTipo());
        assertSame(admin, movimiento.getUsuario());
        assertSame(venta, movimiento.getVenta());
        assertEquals("Anulación de venta 7", movimiento.getObservacion());
        verify(ventas, never()).delete(any());
    }

    @Test
    void permiteDevolverStockDeProductoDadoDeBaja() {
        producto.setActivo(false);
        when(ventas.findByIdParaActualizar(7L)).thenReturn(Optional.of(venta));
        when(usuarios.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(productos.findByIdParaActualizar(1L)).thenReturn(Optional.of(producto));
        servicio.anular(7L, "admin@example.com");
        assertEquals(10, producto.getStockActual());
    }

    @Test
    void rechazaSegundaAnulacionSinTocarStock() {
        venta.setAnulada(true);
        when(ventas.findByIdParaActualizar(7L)).thenReturn(Optional.of(venta));
        assertThrows(NegocioException.class, () -> servicio.anular(7L, "admin@example.com"));
        assertEquals(8, producto.getStockActual());
        verifyNoInteractions(productos, movimientos);
    }

    @Test
    void ventaAusenteDevuelve404() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.anular(99L, "admin@example.com"));
        verifyNoInteractions(productos, movimientos);
    }

    @Test
    void bloqueaProductosEnOrdenAunqueDetalleVengaInvertido() {
        Producto otro = new Producto();
        otro.setId(2L);
        otro.setNombre("Arroz");
        otro.setStockActual(5);
        venta.getDetalles().addFirst(new DetalleVenta(otro, 1, BigDecimal.ONE));
        when(ventas.findByIdParaActualizar(7L)).thenReturn(Optional.of(venta));
        when(usuarios.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(productos.findByIdParaActualizar(1L)).thenReturn(Optional.of(producto));
        when(productos.findByIdParaActualizar(2L)).thenReturn(Optional.of(otro));
        servicio.anular(7L, "admin@example.com");
        var orden = inOrder(productos);
        orden.verify(productos).findByIdParaActualizar(1L);
        orden.verify(productos).findByIdParaActualizar(2L);
        assertEquals(6, otro.getStockActual());
    }

    @Test
    void evitaDesbordamientoDelStock() {
        producto.setStockActual(Integer.MAX_VALUE);
        when(ventas.findByIdParaActualizar(7L)).thenReturn(Optional.of(venta));
        when(usuarios.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(productos.findByIdParaActualizar(1L)).thenReturn(Optional.of(producto));
        assertThrows(NegocioException.class, () -> servicio.anular(7L, "admin@example.com"));
        assertFalse(venta.isAnulada());
        verifyNoInteractions(movimientos);
    }
}
