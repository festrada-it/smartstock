package com.smartstock.core.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.smartstock.core.dto.VentaRequest;
import com.smartstock.core.exception.NegocioException;

/** Requiere PostgreSQL con database/init aplicado; CI lo ejecuta automáticamente. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SMARTSTOCK_INTEGRACION", matches = "true")
class AnulacionIntegracionTest {
    @Autowired VentaService servicio;
    @Autowired JdbcTemplate db;
    private Long usuarioId;
    private Long primero;
    private Long segundo;
    private String email;

    @BeforeEach
    void preparar() {
        String sufijo = UUID.randomUUID().toString();
        email = "it-" + sufijo + "@example.com";
        usuarioId = db.queryForObject("INSERT INTO usuarios(nombre,email,password_hash,rol) VALUES ('Prueba',?,'sin-login','ADMIN') RETURNING id", Long.class, email);
        primero = producto("IT-A-" + sufijo);
        segundo = producto("IT-B-" + sufijo);
    }

    private Long producto(String sku) {
        return db.queryForObject("INSERT INTO productos(sku,nombre,precio,stock_actual) VALUES (?,'Prueba',10,10) RETURNING id", Long.class, sku);
    }

    @AfterEach
    void limpiar() {
        db.update("DELETE FROM movimientos_stock WHERE producto_id IN (?,?)", primero, segundo);
        db.update("DELETE FROM detalle_venta WHERE producto_id IN (?,?)", primero, segundo);
        db.update("DELETE FROM ventas WHERE usuario_id = ?", usuarioId);
        db.update("DELETE FROM productos WHERE id IN (?,?)", primero, segundo);
        db.update("DELETE FROM usuarios WHERE id = ?", usuarioId);
    }

    @Test
    void dosAnulacionesSimultaneasDevuelvenStockUnaSolaVez() throws Exception {
        Long id = servicio.registrar(new VentaRequest(List.of(new VentaRequest.Item(primero, 2))), email).id();
        CountDownLatch inicio = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var tarea = (java.util.concurrent.Callable<Boolean>) () -> {
                inicio.await();
                try {
                    servicio.anular(id, email);
                    return true;
                } catch (NegocioException e) {
                    assertTrue(e.getMessage().contains("ya está anulada"));
                    return false;
                }
            };
            var a = ejecutor.submit(tarea);
            var b = ejecutor.submit(tarea);
            inicio.countDown();
            assertNotEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
        assertEquals(10, stock(primero));
        assertEquals(1, ajustes(id));
        assertTrue(db.queryForObject("SELECT anulada FROM ventas WHERE id=?", Boolean.class, id));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM detalle_venta WHERE venta_id=?", Integer.class, id));
        assertFalse(servicio.ventasDelDia().stream().anyMatch(v -> v.id().equals(id)));
    }

    @Test
    void errorEnSegundoProductoReviertePrimeroEstadoYMovimientos() {
        Long id = servicio.registrar(new VentaRequest(List.of(
                new VentaRequest.Item(primero, 1), new VentaRequest.Item(segundo, 1))), email).id();
        db.update("UPDATE productos SET stock_actual=? WHERE id=?", Integer.MAX_VALUE, segundo);

        assertThrows(NegocioException.class, () -> servicio.anular(id, email));

        assertEquals(9, stock(primero));
        assertEquals(Integer.MAX_VALUE, stock(segundo));
        assertFalse(db.queryForObject("SELECT anulada FROM ventas WHERE id=?", Boolean.class, id));
        assertEquals(0, ajustes(id));
    }

    @Test
    void devuelveStockDeInactivoYConservaAuditoria() {
        Long id = servicio.registrar(new VentaRequest(List.of(new VentaRequest.Item(primero, 2))), email).id();
        db.update("UPDATE productos SET activo=false WHERE id=?", primero);
        servicio.anular(id, email);
        assertEquals(10, stock(primero));
        assertEquals(usuarioId, db.queryForObject("SELECT usuario_id FROM movimientos_stock WHERE venta_id=? AND tipo='AJUSTE'", Long.class, id));
    }

    @Test
    void restauraItemsRepetidosSinPerderCantidades() {
        Long id = servicio.registrar(new VentaRequest(List.of(
                new VentaRequest.Item(primero, 1), new VentaRequest.Item(primero, 2))), email).id();
        assertEquals(7, stock(primero));
        servicio.anular(id, email);
        assertEquals(10, stock(primero));
        assertEquals(2, ajustes(id));
        assertThrows(NegocioException.class, () -> servicio.anular(id, email));
        assertEquals(10, stock(primero));
    }

    private int stock(Long id) {
        return db.queryForObject("SELECT stock_actual FROM productos WHERE id=?", Integer.class, id);
    }

    private int ajustes(Long id) {
        return db.queryForObject("SELECT COUNT(*) FROM movimientos_stock WHERE venta_id=? AND tipo='AJUSTE'", Integer.class, id);
    }
}
