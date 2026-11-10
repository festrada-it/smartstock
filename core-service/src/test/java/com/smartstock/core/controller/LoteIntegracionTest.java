package com.smartstock.core.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "SMARTSTOCK_INTEGRACION", matches = "true")
class LoteIntegracionTest {
    @Autowired TestRestTemplate http;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    Long productoId;
    Long proveedorId;
    final LocalDate hoy = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));

    @BeforeEach void preparar() {
        proveedorId = db.queryForObject("SELECT MIN(id) FROM proveedores WHERE activo", Long.class);
        productoId = db.queryForObject("INSERT INTO productos(sku,nombre,precio,costo,stock_actual,proveedor_id,marca) VALUES (?,'Lotes IT',10,4.25,10,?,'Marca IT') RETURNING id",
                Long.class, "LOTE-" + UUID.randomUUID(), proveedorId);
    }
    @AfterEach void limpiar() {
        db.update("DELETE FROM movimientos_stock WHERE producto_id=?", productoId);
        db.update("DELETE FROM ventas WHERE id IN (SELECT venta_id FROM detalle_venta WHERE producto_id=?)", productoId);
        db.update("DELETE FROM productos WHERE id=?", productoId);
    }

    @Test void compraCreaLoteMovimientoYProximoVencimientoSinPerderApertura() {
        var respuesta = compra(Map.of("costoUnitario", 7.125, "fechaVencimiento", hoy.plusDays(3).toString(), "fechaEntrega", hoy.minusDays(1).toString()), Rol.VENDEDOR);
        assertEquals(201, respuesta.getStatusCode().value());
        assertEquals(12, respuesta.getBody().get("stockActual").asInt());
        assertEquals(7.13, respuesta.getBody().get("costo").asDouble());
        assertEquals(hoy.plusDays(3).toString(), respuesta.getBody().get("proximoVencimiento").asText());
        var lotes = pedir(HttpMethod.GET, rutaLotes(), null, Rol.ADMIN).getBody();
        assertTrue(lotes.isArray(), "Debe existir la consulta de lotes");
        assertEquals(2, lotes.size());
        var lote = lotes.get(0);
        assertFalse(lote.get("esApertura").asBoolean());
        assertEquals(2, lote.get("cantidadInicial").asInt());
        assertEquals(2, lote.get("cantidadDisponible").asInt());
        assertEquals(7.13, lote.get("costoUnitario").asDouble());
        assertEquals(proveedorId.longValue(), lote.get("proveedorId").asLong());
        assertTrue(lote.hasNonNull("proveedor"));
        assertEquals(hoy.minusDays(1).toString(), lote.get("fechaEntrega").asText());
        var historial = pedir(HttpMethod.GET, "/api/productos/" + productoId + "/movimientos", null, Rol.ADMIN).getBody().get("contenido");
        assertEquals(lote.get("id").asLong(), historial.get(0).get("loteId").asLong());
        assertEquals("COMPRA", historial.get(0).get("tipo").asText());
        assertEquals(hoy.plusDays(3).toString(), pedir(HttpMethod.GET, "/api/productos/" + productoId, null, Rol.ADMIN).getBody().get("proximoVencimiento").asText());
        invariante();
    }

    @Test void compraAnteriorUsaCostoProveedorYEntregaPorDefecto() {
        var respuesta = compra(Map.of(), Rol.ADMIN);
        assertEquals(201, respuesta.getStatusCode().value());
        assertTrue(respuesta.getBody().get("proximoVencimiento").isNull());
        var lote = db.queryForMap("SELECT * FROM lotes WHERE producto_id=? AND NOT es_apertura", productoId);
        assertEquals(proveedorId, ((Number) lote.get("proveedor_id")).longValue());
        assertEquals(hoy, ((java.sql.Date) lote.get("fecha_entrega")).toLocalDate());
        assertEquals(new java.math.BigDecimal("4.25"), lote.get("costo_unitario"));
        assertNull(lote.get("fecha_vencimiento"));
        invariante();
    }

    @Test void validaFechasProveedorCostoCantidadYDesbordeSinEscriturasParciales() {
        for (var datos : List.of(Map.of("fechaVencimiento", hoy.minusDays(1).toString()), Map.of("costoUnitario", -1), Map.of("costoUnitario", 10000000000L), Map.of("cantidad", 0), Map.of("cantidad", 1.5), Map.of("fechaEntrega", "mal"))) {
            assertEquals(400, compra(datos, Rol.ADMIN).getStatusCode().value(), datos.toString());
        }
        assertEquals(404, compra(Map.of("proveedorId", Long.MAX_VALUE), Rol.ADMIN).getStatusCode().value());
        db.update("UPDATE productos SET stock_actual=? WHERE id=?", Integer.MAX_VALUE, productoId);
        var desborde = compra(Map.of(), Rol.ADMIN);
        assertEquals(400, desborde.getStatusCode().value());
        assertTrue(desborde.getBody().get("error").asText().contains("máximo"));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM movimientos_stock WHERE producto_id=?", Integer.class, productoId));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM lotes WHERE producto_id=?", Integer.class, productoId));
    }

    @Test void proveedorInactivoYProductoInactivoNoAdmitenIngresos() {
        Long proveedor = db.queryForObject("INSERT INTO proveedores(nombre,activo,lead_time_dias,calidad,clasificacion) VALUES (?,false,1,'BUENA',3) RETURNING id", Long.class, "Inactivo " + UUID.randomUUID());
        try {
            assertEquals(404, compra(Map.of("proveedorId", proveedor), Rol.ADMIN).getStatusCode().value());
            db.update("UPDATE productos SET proveedor_id=? WHERE id=?", proveedor, productoId);
            assertEquals(404, compra(Map.of(), Rol.ADMIN).getStatusCode().value());
            db.update("UPDATE productos SET proveedor_id=null,activo=false WHERE id=?", productoId);
            assertEquals(404, compra(Map.of(), Rol.ADMIN).getStatusCode().value());
        } finally {
            db.update("UPDATE productos SET proveedor_id=null WHERE id=?", productoId);
            db.update("DELETE FROM proveedores WHERE id=?", proveedor);
        }
    }

    @Test void consultaIncluyeHistoricoEInactivosOrdenaNulosAlFinalYConservaVencidos() {
        compra(Map.of("fechaVencimiento", hoy.toString()), Rol.ADMIN);
        db.update("UPDATE lotes SET fecha_vencimiento=? WHERE producto_id=? AND NOT es_apertura", hoy.minusDays(2), productoId);
        assertEquals(hoy.minusDays(2).toString(), pedir(HttpMethod.GET, "/api/productos/" + productoId, null, Rol.VENDEDOR).getBody().get("proximoVencimiento").asText());
        db.update("UPDATE lotes SET cantidad_disponible=0 WHERE producto_id=? AND es_apertura", productoId);
        db.update("UPDATE productos SET stock_actual=2,activo=false WHERE id=?", productoId);
        assertEquals(1, pedir(HttpMethod.GET, rutaLotes(), null, Rol.VENDEDOR).getBody().size());
        var todos = pedir(HttpMethod.GET, rutaLotes() + "?todos=true", null, Rol.ADMIN).getBody();
        assertEquals(2, todos.size());
        assertTrue(todos.get(1).get("fechaVencimiento").isNull());
        assertEquals(401, pedir(HttpMethod.GET, rutaLotes(), null, null).getStatusCode().value());
        assertEquals(404, pedir(HttpMethod.GET, "/api/productos/9223372036854775807/lotes", null, Rol.ADMIN).getStatusCode().value());
        invariante();
    }

    @Test void ajustesCatalogoVentaYAnulacionMantienenInvarianteYCompatibilidadPut() {
        var datos = new HashMap<String,Object>(Map.of("sku", "IT-" + UUID.randomUUID(), "nombre", "Ajuste IT", "precio", 10, "costo", 4.25, "stockActual", 14, "stockMinimo", 30));
        var editado = pedir(HttpMethod.PUT, "/api/productos/" + productoId, datos, Rol.ADMIN);
        assertEquals(200, editado.getStatusCode().value());
        assertEquals("Marca IT", editado.getBody().get("marca").asText());
        assertEquals(proveedorId.longValue(), editado.getBody().get("proveedorId").asLong());
        assertEquals(4, db.queryForObject("SELECT cantidad FROM movimientos_stock WHERE producto_id=? AND tipo='AJUSTE'", Integer.class, productoId));
        invariante();
        compra(Map.of(), Rol.ADMIN); invariante();
        var venta = pedir(HttpMethod.POST, "/api/ventas", Map.of("items", List.of(Map.of("productoId", productoId, "cantidad", 15))), Rol.VENDEDOR);
        assertEquals(201, venta.getStatusCode().value()); invariante();
        var anulada = pedir(HttpMethod.POST, "/api/ventas/" + venta.getBody().get("id").asLong() + "/anular", null, Rol.ADMIN);
        assertEquals(200, anulada.getStatusCode().value()); invariante();
        datos.put("stockActual", 1);
        assertEquals(200, pedir(HttpMethod.PUT, "/api/productos/" + productoId, datos, Rol.ADMIN).getStatusCode().value());
        invariante();
        assertEquals(1, db.queryForObject("SELECT stock_actual FROM productos WHERE id=?", Integer.class, productoId));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM lotes WHERE producto_id=? AND es_apertura", Integer.class, productoId));
    }

    @Test void catalogoCreaAperturaYCompraSinProveedorPermiteNull() {
        db.update("DELETE FROM productos WHERE id=?", productoId);
        var creado = pedir(HttpMethod.POST, "/api/productos", Map.of("sku", "IT-" + UUID.randomUUID(), "nombre", "Creado", "precio", 10, "costo", 3, "stockActual", 5), Rol.ADMIN);
        assertEquals(201, creado.getStatusCode().value()); productoId = creado.getBody().get("id").asLong();
        invariante();
        assertEquals(201, compra(Map.of("fechaVencimiento", hoy.toString()), Rol.VENDEDOR).getStatusCode().value());
        assertNull(db.queryForObject("SELECT proveedor_id FROM lotes WHERE producto_id=? AND NOT es_apertura", Long.class, productoId));
        invariante();
    }

    @Test void comprasConcurrentesNoPierdenStockNiCreanDosAperturas() throws Exception {
        var inicio = new java.util.concurrent.CountDownLatch(1);
        try (var ejecutor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tarea = (java.util.concurrent.Callable<Integer>) () -> {
                inicio.await();
                return compra(Map.of(), Rol.ADMIN).getStatusCode().value();
            };
            var a = ejecutor.submit(tarea); var b = ejecutor.submit(tarea); inicio.countDown();
            assertEquals(201, a.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(201, b.get(20, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(14, db.queryForObject("SELECT stock_actual FROM productos WHERE id=?", Integer.class, productoId));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM lotes WHERE producto_id=? AND es_apertura", Integer.class, productoId));
        invariante();
    }

    @Test void agotarLoteActualizaProximoVencimientoYSoloElHistoricoIncluyeAgotados() {
        assertEquals(201, compra(Map.of("fechaVencimiento", hoy.toString()), Rol.ADMIN).getStatusCode().value());
        var venta = pedir(HttpMethod.POST, "/api/ventas", Map.of("items", List.of(Map.of("productoId", productoId, "cantidad", 12))), Rol.VENDEDOR);
        assertEquals(201, venta.getStatusCode().value());
        invariante();
        assertTrue(pedir(HttpMethod.GET, "/api/productos/" + productoId, null, Rol.ADMIN).getBody().get("proximoVencimiento").isNull());
        assertTrue(pedir(HttpMethod.GET, rutaLotes(), null, Rol.ADMIN).getBody().isEmpty());
        assertEquals(2, pedir(HttpMethod.GET, rutaLotes() + "?todos=true", null, Rol.VENDEDOR).getBody().size());
    }

    void invariante() {
        assertTrue(db.queryForObject("SELECT stock_actual=(SELECT COALESCE(SUM(cantidad_disponible),0) FROM lotes WHERE producto_id=p.id) FROM productos p WHERE id=?", Boolean.class, productoId));
    }
    String rutaLotes() { return "/api/productos/" + productoId + "/lotes"; }
    ResponseEntity<JsonNode> compra(Map<String,?> extras, Rol rol) {
        var datos = new HashMap<String,Object>(); datos.put("productoId", productoId); datos.put("cantidad", 2); datos.putAll(extras);
        return pedir(HttpMethod.POST, "/api/compras", datos, rol);
    }
    ResponseEntity<JsonNode> pedir(HttpMethod metodo, String ruta, Object datos, Rol rol) {
        var h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        if (rol != null) {
            var u = new Usuario(); u.setEmail("admin@smartstock.com"); u.setNombre("IT"); u.setRol(rol); h.setBearerAuth(jwt.generar(u));
        }
        return http.exchange(ruta, metodo, new HttpEntity<>(datos,h), JsonNode.class);
    }
}
