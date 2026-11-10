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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartstock.core.security.JwtService;
import com.smartstock.core.model.Usuario;
import com.smartstock.core.model.Rol;
import com.smartstock.core.dto.VentaRequest;
import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.exception.StockInsuficienteException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "SMARTSTOCK_INTEGRACION", matches = "true")
class FefoIntegracionTest {
    @Autowired VentaService ventas;
    @Autowired JdbcTemplate db;
    @Autowired TestRestTemplate http;
    @Autowired JwtService jwt;
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
        db.update("DELETE FROM ventas_perdidas WHERE usuario_id=(SELECT id FROM usuarios WHERE email=?)", email);
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
    @Test void noVendeVencidosYOrdenaEmpatesConNullAlFinal() {
        Long sinFecha = lote(producto, 2, null, true);
        Long empatado1 = lote(producto, 2, hoy, false);
        Long vencido = lote(producto, 1, hoy.minusDays(3), false);
        Long agotado = lote(producto, 0, hoy.minusDays(10), false);
        Long empatado2 = lote(producto, 2, hoy, false);
        Long venta = vender(5);
        assertEquals(List.of(empatado1, empatado2, sinFecha), db.queryForList("SELECT c.lote_id FROM consumos_lote c JOIN detalle_venta d ON d.id=c.detalle_venta_id WHERE d.venta_id=? ORDER BY c.id", Long.class, venta));
        assertEquals(1, disponible(vencido)); assertEquals(1, disponible(sinFecha)); assertEquals(0, disponible(agotado)); invariante();
    }
    @Test void elStockVencidoNoCuentaComoDisponibleYRegistraLaVentaPerdida() {
        Long vigente = lote(producto, 2, hoy, true);
        Long vencido = lote(producto, 3, hoy.minusDays(1), false);
        var error = assertThrows(StockInsuficienteException.class, () -> vender(4));
        assertEquals(1, error.getFaltantes().size());
        assertEquals(2, error.getFaltantes().get(0).disponible());
        assertEquals(2, error.getFaltantes().get(0).faltante());
        assertEquals(2, disponible(vigente)); assertEquals(3, disponible(vencido));
        assertEquals(5, db.queryForObject("SELECT stock_actual FROM productos WHERE id=?", Integer.class, producto));
        assertEquals(1, perdidas());
        assertEquals(2, db.queryForObject("SELECT cantidad_disponible FROM ventas_perdidas WHERE producto_id=?", Integer.class, producto));
        invariante();
    }
    @Test void soloConLotesVencidosRechazaConDisponibleCero() {
        lote(producto, 3, hoy.minusDays(1), true);
        var error = assertThrows(StockInsuficienteException.class, () -> vender(1));
        assertEquals(0, error.getFaltantes().get(0).disponible());
        assertEquals(1, perdidas()); invariante();
    }
    @Test void stockInsuficienteNoCreaLaVentaNiConsumeYRegistraElFaltante() {
        Long primero = lote(producto, 3, hoy, true);
        Long segundo = producto(); lote(segundo, 1, hoy, true);
        assertThrows(StockInsuficienteException.class, () -> ventas.registrar(new VentaRequest(List.of(new VentaRequest.Item(producto, 2), new VentaRequest.Item(segundo, 2))), email));
        assertEquals(3, disponible(primero)); assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM ventas v JOIN usuarios u ON u.id=v.usuario_id WHERE u.email=?", Integer.class, email));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM consumos_lote c JOIN lotes l ON l.id=c.lote_id WHERE l.producto_id=?", Integer.class, producto)); invariante();
        assertEquals(1, perdidas());
        assertEquals(segundo, db.queryForObject("SELECT producto_id FROM ventas_perdidas vp JOIN usuarios u ON u.id=vp.usuario_id WHERE u.email=?", Long.class, email));
    }
    @Test void inconsistenciaLotesRevierteStockMovimientosYConsumos() {
        Long primero = lote(producto, 2, hoy, true);
        db.update("UPDATE productos SET stock_actual=5 WHERE id=?", producto);
        assertThrows(NegocioException.class, () -> vender(4));
        assertEquals(2, disponible(primero)); assertEquals(5, db.queryForObject("SELECT stock_actual FROM productos WHERE id=?", Integer.class, producto));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM movimientos_stock WHERE producto_id=?", Integer.class, producto));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM consumos_lote WHERE lote_id=?", Integer.class, primero));
        assertEquals(0, perdidas());
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
    @Test void crearGeneraUnaAdvertenciaDeMinimoPorProductoAunqueConsumaDosLotes() {
        lote(producto, 2, hoy, true); lote(producto, 3, hoy.plusDays(1), false);
        var creado = crearPedido(List.of(Map.of("productoId", producto, "cantidad", 5)));
        assertEquals(201, creado.getStatusCode().value());
        assertEquals(5, creado.getBody().get("items").get(0).get("cantidad").asInt());
        long advertenciasMinimo = java.util.stream.StreamSupport.stream(creado.getBody().get("advertencias").spliterator(), false)
                .filter(a -> a.asText().contains("menor o igual al mínimo")).count();
        assertEquals(1, advertenciasMinimo);
        invariante();
    }

    @Test void pedidoReservaAlCrearConfirmarNoMueveStockYCancelaUnaVezEnLosLotesOriginales() throws Exception {
        Long tarde = lote(producto, 3, hoy.plusDays(5), true);
        Long pronto = lote(producto, 2, hoy, false);
        var creado = crearPedido(List.of(Map.of("productoId", producto, "cantidad", 5)));
        assertEquals(201, creado.getStatusCode().value());
        Long id = creado.getBody().get("id").asLong();
        assertEquals("PENDIENTE", creado.getBody().get("estado").asText());
        assertEquals(45, creado.getBody().get("total").asDouble());
        assertEquals(0, disponible(tarde)); assertEquals(0, disponible(pronto));
        assertEquals(List.of(pronto, tarde), db.queryForList("SELECT c.lote_id FROM consumos_lote c JOIN detalle_venta d ON d.id=c.detalle_venta_id WHERE d.venta_id=? ORDER BY c.id", Long.class, id));
        invariante();
        var confirmado = pedir("/api/pedidos/" + id + "/confirmar", null);
        assertEquals(200, confirmado.getStatusCode().value());
        assertEquals("CONFIRMADO", confirmado.getBody().get("estado").asText());
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM movimientos_stock WHERE venta_id=?", Integer.class, id));
        assertEquals(0, disponible(tarde)); assertEquals(0, disponible(pronto));
        CountDownLatch inicio = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> tarea = () -> { inicio.await(); return pedir("/api/pedidos/" + id + "/cancelar", null).getStatusCode().value(); };
            var a = ejecutor.submit(tarea); var b = ejecutor.submit(tarea); inicio.countDown();
            assertEquals(Set.of(200, 400), Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
        }
        assertEquals(3, disponible(tarde)); assertEquals(2, disponible(pronto));
        assertEquals(List.of(2, 3), db.queryForList("SELECT cantidad FROM movimientos_stock WHERE venta_id=? AND tipo='AJUSTE' ORDER BY id", Integer.class, id));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM consumos_lote c JOIN detalle_venta d ON d.id=c.detalle_venta_id WHERE d.venta_id=?", Integer.class, id));
        assertTrue(db.queryForList("SELECT observacion FROM movimientos_stock WHERE venta_id=? AND tipo='AJUSTE'", String.class, id).stream().allMatch(o -> o.equals("Cancelación de pedido " + confirmado.getBody().get("numeroOrden").asText())));
        invariante();
    }
    @Test void cancelarDevuelveAlLoteOriginalAunqueHayaVencidoYNoSePuedeRevender() {
        Long lote = lote(producto, 3, hoy, true);
        var creado = crearPedido(List.of(Map.of("productoId", producto, "cantidad", 3)));
        Long id = creado.getBody().get("id").asLong();
        db.update("UPDATE lotes SET fecha_vencimiento=? WHERE id=?", hoy.minusDays(1), lote);
        assertEquals(200, pedir("/api/pedidos/" + id + "/cancelar", null).getStatusCode().value());
        assertEquals(3, disponible(lote)); invariante();
        var error = assertThrows(StockInsuficienteException.class, () -> vender(1));
        assertEquals(0, error.getFaltantes().get(0).disponible());
    }
    @Test void lotesInconsistentesAlCrearPedidoRechazanSinCrearNadaNiVentaPerdida() {
        Long primero = lote(producto, 3, hoy, true);
        Long segundo = producto(); lote(segundo, 1, hoy, true);
        db.update("UPDATE productos SET stock_actual=3 WHERE id=?", segundo);
        var respuesta = crearPedido(List.of(Map.of("productoId", producto, "cantidad", 2), Map.of("productoId", segundo, "cantidad", 2)));
        assertEquals(400, respuesta.getStatusCode().value());
        assertFalse(respuesta.getBody().has("faltantes"));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM ventas v JOIN usuarios u ON u.id=v.usuario_id WHERE u.email=?", Integer.class, email));
        assertEquals(0, perdidas());
        assertEquals(3, disponible(primero));
    }
    int perdidas() { return db.queryForObject("SELECT COUNT(*) FROM ventas_perdidas vp JOIN usuarios u ON u.id=vp.usuario_id WHERE u.email=?", Integer.class, email); }
    ResponseEntity<JsonNode> crearPedido(List<Map<String, Object>> items) {
        Long cliente = db.queryForObject("SELECT MIN(id) FROM clientes WHERE activo", Long.class);
        return pedir("/api/pedidos", Map.of("clienteId", cliente, "formaPago", "EFECTIVO", "descuentoPct", 10, "items", items));
    }
    ResponseEntity<JsonNode> pedir(String ruta, Object datos) {
        var usuario = new Usuario(); usuario.setEmail(email); usuario.setNombre("FEFO"); usuario.setRol(Rol.ADMIN);
        var headers = new HttpHeaders(); headers.setBearerAuth(jwt.generar(usuario)); headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(ruta, HttpMethod.POST, new HttpEntity<>(datos, headers), JsonNode.class);
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
