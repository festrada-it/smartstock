package com.smartstock.core.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartstock.core.model.*;
import com.smartstock.core.security.JwtService;
import com.smartstock.core.service.LoteService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "SMARTSTOCK_INTEGRACION", matches = "true")
class RetiroLoteIntegracionTest {
    @Autowired TestRestTemplate http;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    Long producto;
    final LocalDate hoy = LoteService.hoy();

    @BeforeEach void preparar() {
        producto = db.queryForObject("INSERT INTO productos(sku,nombre,precio,costo,stock_actual) VALUES (?,'Yogur IT',10,4,0) RETURNING id",
                Long.class, "RETIRO-" + UUID.randomUUID());
    }

    @AfterEach void limpiar() {
        db.update("DELETE FROM movimientos_stock WHERE producto_id=?", producto);
        db.update("DELETE FROM ventas WHERE id IN (SELECT venta_id FROM detalle_venta WHERE producto_id=?)", producto);
        db.update("DELETE FROM ventas_perdidas WHERE producto_id=?", producto);
        db.update("DELETE FROM productos WHERE id=?", producto);
    }

    @Test void vendedorRetiraElLoteVencidoCompletoYRegistraElAjuste() {
        Long vencido = lote(12, hoy.minusDays(1), true);
        Long vigente = lote(30, hoy.plusDays(10), false);
        var respuesta = retirar(vencido, Map.of("observacion", "  Remito 0001-45 "), Rol.VENDEDOR);
        assertEquals(200, respuesta.getStatusCode().value(), respuesta.toString());
        var cuerpo = respuesta.getBody();
        assertEquals(vencido, cuerpo.get("loteId").asLong());
        assertEquals(producto, cuerpo.get("productoId").asLong());
        assertEquals("Yogur IT", cuerpo.get("producto").asText());
        assertEquals(hoy.minusDays(1).toString(), cuerpo.get("fechaVencimiento").asText());
        assertEquals(12, cuerpo.get("cantidadRetirada").asInt());
        assertEquals(30, cuerpo.get("stockActual").asInt());

        assertEquals(0, disponible(vencido));
        assertEquals(30, disponible(vigente));
        assertEquals(30, stock());
        var movimiento = db.queryForMap("SELECT tipo,cantidad,lote_id,observacion,usuario_id FROM movimientos_stock WHERE producto_id=?", producto);
        assertEquals("AJUSTE", movimiento.get("tipo"));
        assertEquals(-12, movimiento.get("cantidad"));
        assertEquals(vencido, ((Number) movimiento.get("lote_id")).longValue());
        assertEquals("Retiro de lote vencido: Remito 0001-45", movimiento.get("observacion"));
        assertEquals(db.queryForObject("SELECT id FROM usuarios WHERE email='admin@smartstock.com'", Long.class),
                ((Number) movimiento.get("usuario_id")).longValue());
        invariante();
    }

    @Test void sinCuerpoUsaLaObservacionFijaYTrasElRetiroSeVendeElLoteVigente() {
        Long vencido = lote(5, hoy.minusDays(3), true);
        lote(4, hoy, false);
        assertEquals(200, retirar(vencido, null, Rol.ADMIN).getStatusCode().value());
        assertEquals("Retiro de lote vencido", db.queryForObject("SELECT observacion FROM movimientos_stock WHERE producto_id=?", String.class, producto));
        var venta = pedir("/api/ventas", Map.of("items", List.of(Map.of("productoId", producto, "cantidad", 4))), Rol.VENDEDOR);
        assertEquals(201, venta.getStatusCode().value(), venta.toString());
        assertEquals(0, stock());
        invariante();
    }

    @Test void rechazaLotesQueNoEstanVencidos() {
        Long venceHoy = lote(3, hoy, true);
        Long sinVencimiento = lote(2, null, false);
        Long futuro = lote(1, hoy.plusDays(1), false);
        for (Long lote : List.of(venceHoy, sinVencimiento, futuro)) {
            var respuesta = retirar(lote, null, Rol.ADMIN);
            assertEquals(400, respuesta.getStatusCode().value());
            assertEquals("Solo se pueden retirar lotes vencidos", respuesta.getBody().get("error").asText());
        }
        assertEquals(6, stock());
        assertEquals(0, movimientos());
        invariante();
    }

    @Test void unLoteYaRetiradoNoSeRetiraDosVecesNiConcurrentemente() throws Exception {
        Long vencido = lote(8, hoy.minusDays(1), true);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Integer> tarea = () -> { inicio.await(); return retirar(vencido, null, Rol.VENDEDOR).getStatusCode().value(); };
            var a = ejecutor.submit(tarea);
            var b = ejecutor.submit(tarea);
            inicio.countDown();
            assertEquals(Set.of(200, 400), Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
        }
        assertEquals(400, retirar(vencido, null, Rol.ADMIN).getStatusCode().value());
        assertEquals(0, stock());
        assertEquals(1, movimientos());
        invariante();
    }

    @Test void retiraAunqueElProductoEsteDadoDeBaja() {
        Long vencido = lote(2, hoy.minusDays(1), true);
        db.update("UPDATE productos SET activo=false WHERE id=?", producto);
        assertEquals(200, retirar(vencido, null, Rol.VENDEDOR).getStatusCode().value());
        assertEquals(0, stock());
        invariante();
    }

    @Test void validaLoteInexistenteObservacionLargaYToken() {
        Long vencido = lote(2, hoy.minusDays(1), true);
        assertEquals(404, retirar(Long.MAX_VALUE, null, Rol.ADMIN).getStatusCode().value());
        assertEquals(400, retirar(vencido, Map.of("observacion", "x".repeat(231)), Rol.ADMIN).getStatusCode().value());
        assertEquals(401, retirar(vencido, null, null).getStatusCode().value());
        assertEquals(2, stock());
        assertEquals(0, movimientos());
    }

    Long lote(int cantidad, LocalDate vencimiento, boolean apertura) {
        db.update("UPDATE productos SET stock_actual=stock_actual+? WHERE id=?", cantidad, producto);
        return db.queryForObject("INSERT INTO lotes(producto_id,cantidad_inicial,cantidad_disponible,fecha_vencimiento,fecha_entrega,costo_unitario,es_apertura) VALUES (?,?,?,?,?,4,?) RETURNING id",
                Long.class, producto, cantidad, cantidad, vencimiento, hoy.minusDays(30), apertura);
    }
    int disponible(Long lote) { return db.queryForObject("SELECT cantidad_disponible FROM lotes WHERE id=?", Integer.class, lote); }
    int stock() { return db.queryForObject("SELECT stock_actual FROM productos WHERE id=?", Integer.class, producto); }
    int movimientos() { return db.queryForObject("SELECT count(*) FROM movimientos_stock WHERE producto_id=?", Integer.class, producto); }
    void invariante() {
        assertTrue(db.queryForObject("SELECT stock_actual=(SELECT COALESCE(SUM(cantidad_disponible),0) FROM lotes WHERE producto_id=p.id) FROM productos p WHERE id=?", Boolean.class, producto));
    }
    ResponseEntity<JsonNode> retirar(Long lote, Object datos, Rol rol) { return pedir("/api/lotes/" + lote + "/retirar", datos, rol); }
    ResponseEntity<JsonNode> pedir(String ruta, Object datos, Rol rol) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (rol != null) {
            var usuario = new Usuario();
            usuario.setEmail("admin@smartstock.com");
            usuario.setRol(rol);
            headers.setBearerAuth(jwt.generar(usuario));
        }
        return http.exchange(ruta, HttpMethod.POST, new HttpEntity<>(datos, headers), JsonNode.class);
    }
}
