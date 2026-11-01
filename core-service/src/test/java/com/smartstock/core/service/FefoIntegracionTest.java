package com.smartstock.core.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.smartstock.core.dto.VentaRequest;
import com.smartstock.core.exception.NegocioException;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SMARTSTOCK_INTEGRACION", matches = "true")
class FefoIntegracionTest {
    @Autowired VentaService ventas;
    @Autowired JdbcTemplate db;
    Long producto;
    String email;
    List<Long> productos = new ArrayList<>();
    final LocalDate hoy = LoteService.hoy();

    @BeforeEach void preparar() {
        email = "fefo-" + UUID.randomUUID() + "@example.com";
        db.update("INSERT INTO usuarios(nombre,email,password_hash,rol) VALUES ('FEFO',?,'sin-login','ADMIN')", email);
        producto = producto();
    }
    Long producto() {
        Long id = db.queryForObject("INSERT INTO productos(sku,nombre,precio,costo,stock_actual) VALUES (?,'FEFO',10,2,0) RETURNING id", Long.class, "FEFO-" + UUID.randomUUID());
        productos.add(id); return id;
    }
    Long lote(Long id, int cantidad, LocalDate vencimiento, boolean apertura) {
        db.update("UPDATE productos SET stock_actual=stock_actual+? WHERE id=?", cantidad, id);
        return db.queryForObject("INSERT INTO lotes(producto_id,cantidad_inicial,cantidad_disponible,fecha_vencimiento,fecha_entrega,costo_unitario,es_apertura) VALUES (?,?,?,?,?,2,?) RETURNING id", Long.class, id, cantidad, cantidad, vencimiento, hoy, apertura);
    }
    @AfterEach void limpiar() {
        db.update("DELETE FROM movimientos_stock WHERE usuario_id=(SELECT id FROM usuarios WHERE email=?)", email);
        db.update("DELETE FROM ventas WHERE usuario_id=(SELECT id FROM usuarios WHERE email=?)", email);
        productos.forEach(id -> db.update("DELETE FROM productos WHERE id=?", id));
        db.update("DELETE FROM usuarios WHERE email=?", email);
    }
    Long vender(int cantidad) { return ventas.registrar(new VentaRequest(List.of(new VentaRequest.Item(producto, cantidad))), email).id(); }
    int disponible(Long lote) { return db.queryForObject("SELECT cantidad_disponible FROM lotes WHERE id=?", Integer.class, lote); }
    void invariante() {
        productos.forEach(id -> assertTrue(db.queryForObject("SELECT stock_actual=(SELECT COALESCE(SUM(cantidad_disponible),0) FROM lotes WHERE producto_id=p.id) FROM productos p WHERE id=?", Boolean.class, id)));
    }

    @Test void consumeDosLotesFefoYRestituyeExactamenteConAuditoria() {
        Long tarde = lote(producto, 5, hoy.plusDays(10), true);
        Long pronto = lote(producto, 3, hoy.plusDays(2), false);
        Long venta = vender(6);
        assertEquals(2, disponible(tarde)); assertEquals(0, disponible(pronto));
        assertEquals(List.of(pronto, tarde), db.queryForList("SELECT c.lote_id FROM consumos_lote c JOIN detalle_venta d ON d.id=c.detalle_venta_id WHERE d.venta_id=? ORDER BY c.id", Long.class, venta));
        assertEquals(List.of(-3, -3), db.queryForList("SELECT cantidad FROM movimientos_stock WHERE venta_id=? AND tipo='VENTA' ORDER BY id", Integer.class, venta));
        assertEquals(List.of(pronto, tarde), db.queryForList("SELECT lote_id FROM movimientos_stock WHERE venta_id=? ORDER BY id", Long.class, venta));
        invariante(); ventas.anular(venta, email);
        assertEquals(5, disponible(tarde)); assertEquals(3, disponible(pronto));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM consumos_lote c JOIN detalle_venta d ON d.id=c.detalle_venta_id WHERE d.venta_id=?", Integer.class, venta));
        assertEquals(List.of(pronto, tarde), db.queryForList("SELECT lote_id FROM movimientos_stock WHERE venta_id=? AND tipo='AJUSTE' ORDER BY id", Long.class, venta));
        assertThrows(NegocioException.class, () -> ventas.anular(venta, email)); invariante();
    }
    @Test void ordenaVencidosEmpatesYNullAlFinalYOmitieAgotados() {
        Long sinFecha = lote(producto, 2, null, true);
        Long empatado1 = lote(producto, 2, hoy, false);
        Long vencido = lote(producto, 1, hoy.minusDays(3), false);
        Long agotado = lote(producto, 0, hoy.minusDays(10), false);
        Long empatado2 = lote(producto, 2, hoy, false);
        Long venta = vender(6);
        assertEquals(List.of(vencido, empatado1, empatado2, sinFecha), db.queryForList("SELECT c.lote_id FROM consumos_lote c JOIN detalle_venta d ON d.id=c.detalle_venta_id WHERE d.venta_id=? ORDER BY c.id", Long.class, venta));
        assertEquals(1, disponible(sinFecha)); assertEquals(0, disponible(agotado)); invariante();
    }
    @Test void stockInsuficienteRevierteInclusoLaVentaPersistidaYSusConsumos() {
        Long primero = lote(producto, 3, hoy, true);
        Long segundo = producto(); lote(segundo, 1, hoy, true);
        assertThrows(NegocioException.class, () -> ventas.registrar(new VentaRequest(List.of(new VentaRequest.Item(producto, 2), new VentaRequest.Item(segundo, 2))), email));
        assertEquals(3, disponible(primero)); assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM ventas v JOIN usuarios u ON u.id=v.usuario_id WHERE u.email=?", Integer.class, email));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM consumos_lote c JOIN lotes l ON l.id=c.lote_id WHERE l.producto_id=?", Integer.class, producto)); invariante();
    }
    @Test void inconsistenciaLotesRevierteStockMovimientosYConsumos() {
        Long primero = lote(producto, 2, hoy, true);
        db.update("UPDATE productos SET stock_actual=5 WHERE id=?", producto);
        assertThrows(NegocioException.class, () -> vender(4));
        assertEquals(2, disponible(primero)); assertEquals(5, db.queryForObject("SELECT stock_actual FROM productos WHERE id=?", Integer.class, producto));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM movimientos_stock WHERE producto_id=?", Integer.class, producto));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM consumos_lote WHERE lote_id=?", Integer.class, primero));
    }
    @Test void concurrenciaSobreMismoLoteSoloVendeUnaVezSinDisponibleNegativo() throws Exception {
        Long primero = lote(producto, 5, hoy, true);
        CountDownLatch inicio = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> tarea = () -> { inicio.await(); try { vender(4); return true; } catch (NegocioException e) { return false; } };
            var a = ejecutor.submit(tarea); var b = ejecutor.submit(tarea); inicio.countDown();
            assertNotEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, disponible(primero));
        assertEquals(4, db.queryForObject("SELECT SUM(cantidad) FROM consumos_lote WHERE lote_id=?", Integer.class, primero)); invariante();
    }
    @Test void historicaSinConsumosDevuelveApertura() {
        Long apertura = lote(producto, 5, null, true);
        Long venta = vender(2);
        db.update("DELETE FROM consumos_lote WHERE detalle_venta_id IN (SELECT id FROM detalle_venta WHERE venta_id=?)", venta);
        ventas.anular(venta, email);
        assertEquals(5, disponible(apertura));
        assertEquals(apertura, db.queryForObject("SELECT lote_id FROM movimientos_stock WHERE venta_id=? AND tipo='AJUSTE'", Long.class, venta)); invariante();
    }
}
