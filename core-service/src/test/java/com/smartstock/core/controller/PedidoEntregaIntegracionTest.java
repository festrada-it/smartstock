package com.smartstock.core.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.ZoneId;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "SMARTSTOCK_INTEGRACION", matches = "true")
class PedidoEntregaIntegracionTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired JwtService jwt;
    Long cliente;
    Long producto;
    Long otro;

    @BeforeEach void preparar() {
        cliente = db.queryForObject("INSERT INTO clientes(nombre,calidad,clasificacion) VALUES ('Entrega IT','BUENA',4) RETURNING id", Long.class);
        producto = producto();
        otro = producto();
    }

    @AfterEach void limpiar() {
        db.update("DELETE FROM movimientos_stock WHERE producto_id IN (?,?)", producto, otro);
        db.update("DELETE FROM ventas WHERE cliente_id=?", cliente);
        db.update("DELETE FROM productos WHERE id IN (?,?)", producto, otro);
        db.update("DELETE FROM clientes WHERE id=?", cliente);
    }

    @Test void cicloCompletoConservaFechaPrecioPactadoYDescuento() {
        var datos = datos(List.of(item(producto, 2)));
        datos.put("descuentoPct", 10);
        long id = crear(datos);
        LocalDate pactada = hoy().plusDays(2);
        var fecha = pedir(HttpMethod.PUT, ruta(id, "fecha-envio"), Map.of("fechaEnvio", pactada.toString()), Rol.VENDEDOR);
        assertEquals(200, fecha.getStatusCode().value());
        assertEquals("PENDIENTE", fecha.getBody().get("estado").asText());
        assertEquals("PACTADA", fecha.getBody().get("estadoEntrega").asText());
        db.update("UPDATE productos SET precio=99 WHERE id=?", producto);
        assertEquals(200, post(id, "confirmar", Rol.VENDEDOR).getStatusCode().value());
        LocalDate cambiada = pactada.plusDays(1);
        assertEquals(200, pedir(HttpMethod.PUT, ruta(id, "fecha-envio"),
                Map.of("fechaEnvio", cambiada.toString()), Rol.ADMIN).getStatusCode().value());
        var enviado = post(id, "enviar", Rol.VENDEDOR);
        assertEquals(200, enviado.getStatusCode().value());
        assertEquals("ENVIADO", enviado.getBody().get("estado").asText());
        assertEquals(cambiada.toString(), enviado.getBody().get("fechaEnvio").asText());
        var entregado = post(id, "entregar", Rol.VENDEDOR);
        assertEquals(200, entregado.getStatusCode().value());
        var pedido = entregado.getBody();
        assertEquals("ENTREGADO", pedido.get("estado").asText());
        assertEquals("ENTREGADA", pedido.get("estadoEntrega").asText());
        assertEquals(20.02, pedido.get("subtotal").asDouble());
        assertEquals(18.02, pedido.get("total").asDouble());
        assertEquals(2.00, pedido.get("importeDescuento").asDouble());
        assertEquals(10.01, pedido.get("items").get(0).get("precioUnitario").asDouble());
        assertTrue(pedido.get("numeroFactura").asText().matches("FAC-0001-\\d{8,}"));
        assertEquals(pedido.get("numeroFactura").asText(),
                db.queryForObject("SELECT numero_factura FROM ventas WHERE id=?", String.class, id));
        assertEquals("ENTREGADA", db.queryForObject("SELECT estado_entrega FROM ventas WHERE id=?", String.class, id));
        assertFalse(db.queryForObject("SELECT anulada FROM ventas WHERE id=?", Boolean.class, id));
        assertEquals(8, stock(producto));
        assertEquals(1, movimientos(id, "VENTA"));
        assertEquals(0, movimientos(id, "AJUSTE"));
    }

    @Test void facturaDePedidoParcialConservaImportesYCantidadEntregada() {
        var datos = datos(List.of(item(producto, 12)));
        datos.put("descuentoPct", 10);
        long id = crear(datos);
        assertEquals(200, post(id, "confirmar", Rol.VENDEDOR).getStatusCode().value());
        assertEquals(200, post(id, "enviar", Rol.VENDEDOR).getStatusCode().value());
        var respuesta = post(id, "entregar", Rol.VENDEDOR);
        assertEquals(200, respuesta.getStatusCode().value());
        var pedido = respuesta.getBody();
        assertEquals(12, linea(pedido, producto).get("cantidadSolicitada").asInt());
        assertEquals(10, linea(pedido, producto).get("cantidadEntregada").asInt());
        assertEquals(2, linea(pedido, producto).get("faltante").asInt());
        assertEquals(100.10, pedido.get("subtotal").asDouble());
        assertEquals(90.09, pedido.get("total").asDouble());
        assertTrue(pedido.get("numeroFactura").asText().matches("FAC-0001-\\d{8,}"));
        assertEquals("ENTREGADA", pedido.get("estadoEntrega").asText());
        assertEquals(0, stock(producto));
        assertEquals(1, movimientos(id, "VENTA"));
    }

    @Test void enviarSinFechaAsignaHoyArgentinaSinNuevoMovimiento() {
        long id = confirmado(List.of(item(producto, 1)));
        var respuesta = post(id, "enviar", Rol.ADMIN);
        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals(hoy().toString(), respuesta.getBody().get("fechaEnvio").asText());
        assertEquals("PACTADA", respuesta.getBody().get("estadoEntrega").asText());
        assertEquals(hoy(), db.queryForObject("SELECT fecha_envio FROM ventas WHERE id=?", LocalDate.class, id));
        assertEquals(9, stock(producto));
        assertEquals(1, movimientos(id, "VENTA"));
    }

    @Test void transicionesInvalidasNoModificanStockNiFacturan() {
        long id = crear(datos(List.of(item(producto, 1))));
        for (String estado : List.of("PENDIENTE", "CONFIRMADO", "ENVIADO", "ENTREGADO", "CANCELADO")) {
            db.update("UPDATE ventas SET estado=? WHERE id=?", estado, id);
            if (!estado.equals("CONFIRMADO")) assertEquals(400, post(id, "enviar", Rol.ADMIN).getStatusCode().value(), estado);
            if (!estado.equals("ENVIADO")) assertEquals(400, post(id, "entregar", Rol.ADMIN).getStatusCode().value(), estado);
            if (!List.of("PENDIENTE", "CONFIRMADO").contains(estado)) {
                assertEquals(400, post(id, "cancelar", Rol.ADMIN).getStatusCode().value(), estado);
                assertEquals(400, pedir(HttpMethod.POST, "/api/ventas/" + id + "/anular", null, Rol.ADMIN).getStatusCode().value());
                assertEquals(400, pedir(HttpMethod.PUT, ruta(id, "fecha-envio"),
                        Map.of("fechaEnvio", hoy().toString()), Rol.ADMIN).getStatusCode().value());
            }
            assertNull(db.queryForObject("SELECT numero_factura FROM ventas WHERE id=?", String.class, id));
        }
        assertEquals(10, stock(producto));
        assertEquals(0, movimientos(id, "AJUSTE"));
    }

    @Test void cancelarPendienteYAliasLegacyNoDevuelvenStock() {
        for (boolean legacy : List.of(false, true)) {
            long id = crear(datos(List.of(item(producto, 4))));
            String ruta = legacy ? "/api/ventas/" + id + "/anular" : ruta(id, "cancelar");
            var respuesta = pedir(HttpMethod.POST, ruta, null, Rol.ADMIN);
            assertEquals(200, respuesta.getStatusCode().value());
            if (legacy) {
                assertTrue(respuesta.getBody().get("anulada").asBoolean());
                assertTrue(respuesta.getBody().hasNonNull("vendedor"));
            } else {
                assertEquals("CANCELADO", respuesta.getBody().get("estado").asText());
            }
            assertEquals("CANCELADO", db.queryForObject("SELECT estado FROM ventas WHERE id=?", String.class, id));
            assertTrue(db.queryForObject("SELECT anulada FROM ventas WHERE id=?", Boolean.class, id));
            assertEquals(0, movimientos(id, "VENTA"));
            assertEquals(0, movimientos(id, "AJUSTE"));
            assertEquals(400, post(id, "cancelar", Rol.ADMIN).getStatusCode().value());
            assertEquals(10, stock(producto));
        }
    }

    @Test void cancelarConfirmadoDevuelveInactivosConObservacionYUsuario() {
        long id = confirmado(List.of(item(producto, 2), item(otro, 3)));
        var antes = pedir(HttpMethod.GET, "/api/pedidos/" + id, null, Rol.ADMIN).getBody();
        db.update("UPDATE productos SET activo=false WHERE id=?", producto);
        var respuesta = post(id, "cancelar", Rol.ADMIN);
        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals("CANCELADO", respuesta.getBody().get("estado").asText());
        assertEquals(antes.get("total"), respuesta.getBody().get("total"));
        assertEquals(antes.get("items"), respuesta.getBody().get("items"));
        assertEquals(10, stock(producto));
        assertEquals(10, stock(otro));
        assertEquals(2, movimientos(id, "AJUSTE"));
        var observaciones = db.queryForList("SELECT observacion FROM movimientos_stock WHERE venta_id=? AND tipo='AJUSTE'", String.class, id);
        assertEquals(List.of("Cancelación de pedido " + antes.get("numeroOrden").asText(),
                "Cancelación de pedido " + antes.get("numeroOrden").asText()), observaciones);
        Long usuario = db.queryForObject("SELECT id FROM usuarios WHERE email='admin@smartstock.com'", Long.class);
        assertEquals(2, db.queryForObject("SELECT count(*) FROM movimientos_stock WHERE venta_id=? AND tipo='AJUSTE' AND usuario_id=?",
                Integer.class, id, usuario));
    }

    @Test void cancelarParcialYAliasDevuelvenSoloEntregadaSinAjustesCero() {
        db.update("UPDATE productos SET stock_actual=0 WHERE id=?", otro);
        for (boolean legacy : List.of(false, true)) {
            long id = confirmado(List.of(item(producto, 12), item(otro, 3)));
            var antes = pedir(HttpMethod.GET, "/api/pedidos/" + id, null, Rol.ADMIN).getBody();
            assertEquals(12, linea(antes, producto).get("cantidadSolicitada").asInt());
            assertEquals(10, linea(antes, producto).get("cantidadEntregada").asInt());
            assertEquals(0, linea(antes, otro).get("cantidadEntregada").asInt());
            String destino = legacy ? "/api/ventas/" + id + "/anular" : ruta(id, "cancelar");
            var respuesta = pedir(HttpMethod.POST, destino, null, Rol.ADMIN);
            assertEquals(200, respuesta.getStatusCode().value());
            assertEquals(10, stock(producto));
            assertEquals(0, stock(otro));
            assertEquals(1, movimientos(id, "AJUSTE"));
            assertEquals(10, db.queryForObject(
                    "SELECT sum(cantidad) FROM movimientos_stock WHERE venta_id=? AND tipo='AJUSTE'", Integer.class, id));
            assertEquals("CANCELADO", db.queryForObject("SELECT estado FROM ventas WHERE id=?", String.class, id));
        }
    }

    @Test void cancelacionConcurrenteConAliasDevuelveUnaSolaVez() throws Exception {
        long id = confirmado(List.of(item(producto, 2)));
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            var cancelar = ejecutor.submit(() -> { inicio.await(); return post(id, "cancelar", Rol.ADMIN).getStatusCode().value(); });
            var anular = ejecutor.submit(() -> {
                inicio.await();
                return pedir(HttpMethod.POST, "/api/ventas/" + id + "/anular", null, Rol.ADMIN).getStatusCode().value();
            });
            inicio.countDown();
            var estados = new ArrayList<>(List.of(cancelar.get(20, TimeUnit.SECONDS), anular.get(20, TimeUnit.SECONDS)));
            Collections.sort(estados);
            assertEquals(List.of(200, 400), estados);
        }
        assertEquals(10, stock(producto));
        assertEquals(1, movimientos(id, "AJUSTE"));
    }

    @Test void entregarConcurrentementeMismoPedidoFacturaUnaSolaVez() throws Exception {
        long id = enviado();
        long secuenciaAntes = secuenciaFactura();
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Integer> entregar = () -> { inicio.await(); return post(id, "entregar", Rol.VENDEDOR).getStatusCode().value(); };
            var primera = ejecutor.submit(entregar);
            var segunda = ejecutor.submit(entregar);
            inicio.countDown();
            var estados = new ArrayList<>(List.of(primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS)));
            Collections.sort(estados);
            assertEquals(List.of(200, 400), estados);
        }
        assertEquals(secuenciaAntes + 1, secuenciaFactura());
        String factura = db.queryForObject("SELECT numero_factura FROM ventas WHERE id=?", String.class, id);
        assertNotNull(factura);
        assertEquals(400, post(id, "entregar", Rol.ADMIN).getStatusCode().value());
        assertEquals(factura, db.queryForObject("SELECT numero_factura FROM ventas WHERE id=?", String.class, id));
        assertEquals(secuenciaAntes + 1, secuenciaFactura());
    }

    @Test void entregasDePedidosConcurrentesUsanFacturasDistintas() throws Exception {
        long primero = enviado();
        long segundo = enviado();
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            var tareas = new ArrayList<Future<String>>();
            for (long id : List.of(primero, segundo)) {
                tareas.add(ejecutor.submit(() -> {
                    inicio.await();
                    var respuesta = post(id, "entregar", Rol.ADMIN);
                    assertEquals(200, respuesta.getStatusCode().value());
                    return respuesta.getBody().get("numeroFactura").asText();
                }));
            }
            inicio.countDown();
            assertNotEquals(tareas.get(0).get(20, TimeUnit.SECONDS), tareas.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    @Test void errorEnSegundoProductoRevierteCancelacionCompleta() {
        long id = confirmado(List.of(item(producto, 1), item(otro, 1)));
        db.update("UPDATE productos SET stock_actual=? WHERE id=?", Integer.MAX_VALUE, otro);
        var respuesta = post(id, "cancelar", Rol.ADMIN);
        assertEquals(400, respuesta.getStatusCode().value());
        assertEquals(9, stock(producto));
        assertEquals(Integer.MAX_VALUE, stock(otro));
        assertEquals("CONFIRMADO", db.queryForObject("SELECT estado FROM ventas WHERE id=?", String.class, id));
        assertFalse(db.queryForObject("SELECT anulada FROM ventas WHERE id=?", Boolean.class, id));
        assertEquals(0, movimientos(id, "AJUSTE"));
    }

    @Test void fechaObligatoriaNoAnteriorAHoyYDisponibleSoloAntesDeEnviar() {
        long id = crear(datos(List.of(item(producto, 1))));
        var nula = new HashMap<String, Object>();
        nula.put("fechaEnvio", null);
        for (Object datos : List.of(Map.of(), nula, Map.of("fechaEnvio", "2026-02-30"),
                Map.of("fechaEnvio", hoy().minusDays(1).toString()))) {
            assertEquals(400, pedir(HttpMethod.PUT, ruta(id, "fecha-envio"), datos, Rol.VENDEDOR).getStatusCode().value());
        }
        assertEquals(200, pedir(HttpMethod.PUT, ruta(id, "fecha-envio"), Map.of("fechaEnvio", hoy().toString()), Rol.ADMIN).getStatusCode().value());
        assertEquals(10, stock(producto));
        assertEquals(200, post(id, "confirmar", Rol.VENDEDOR).getStatusCode().value());
        assertEquals(200, pedir(HttpMethod.PUT, ruta(id, "fecha-envio"), Map.of("fechaEnvio", hoy().plusDays(1).toString()), Rol.VENDEDOR).getStatusCode().value());
        assertEquals(200, post(id, "enviar", Rol.VENDEDOR).getStatusCode().value());
        assertEquals(400, pedir(HttpMethod.PUT, ruta(id, "fecha-envio"), Map.of("fechaEnvio", hoy().toString()), Rol.ADMIN).getStatusCode().value());
        assertEquals(9, stock(producto));
    }

    @Test void rutasNuevasRespetanRolesYErroresJSON() {
        long id = crear(datos(List.of(item(producto, 1))));
        var prohibida = post(id, "cancelar", Rol.VENDEDOR);
        assertEquals(403, prohibida.getStatusCode().value());
        assertEquals("No tenés permiso para realizar esta acción", prohibida.getBody().get("error").asText());
        for (String accion : List.of("enviar", "entregar", "cancelar")) {
            assertEquals(401, post(id, accion, null).getStatusCode().value());
            assertEquals(404, post(Long.MAX_VALUE, accion, Rol.ADMIN).getStatusCode().value());
        }
        assertEquals(401, pedir(HttpMethod.PUT, ruta(id, "fecha-envio"), Map.of("fechaEnvio", hoy().toString()), null).getStatusCode().value());
        assertEquals(404, pedir(HttpMethod.PUT, ruta(Long.MAX_VALUE, "fecha-envio"), Map.of("fechaEnvio", hoy().toString()), Rol.ADMIN).getStatusCode().value());
        assertEquals("PENDIENTE", db.queryForObject("SELECT estado FROM ventas WHERE id=?", String.class, id));
        assertEquals(10, stock(producto));
    }

    long crear(Map<String, Object> datos) {
        var respuesta = pedir(HttpMethod.POST, "/api/pedidos", datos, Rol.VENDEDOR);
        assertEquals(201, respuesta.getStatusCode().value(), respuesta.toString());
        return respuesta.getBody().get("id").asLong();
    }
    long confirmado(List<Map<String, Object>> items) {
        long id = crear(datos(items));
        assertEquals(200, post(id, "confirmar", Rol.VENDEDOR).getStatusCode().value());
        return id;
    }
    long enviado() {
        long id = confirmado(List.of(item(producto, 1)));
        assertEquals(200, post(id, "enviar", Rol.VENDEDOR).getStatusCode().value());
        return id;
    }
    JsonNode linea(JsonNode pedido, long productoId) {
        for (var item : pedido.get("items")) {
            if (item.get("productoId").asLong() == productoId) return item;
        }
        throw new AssertionError("Falta la línea del producto " + productoId);
    }
    String ruta(long id, String accion) { return "/api/pedidos/" + id + "/" + accion; }
    LocalDate hoy() { return LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")); }
    Map<String, Object> item(Long id, int cantidad) { return Map.of("productoId", id, "cantidad", cantidad); }
    Map<String, Object> datos(List<Map<String, Object>> items) {
        return new HashMap<>(Map.of("clienteId", cliente, "formaPago", "EFECTIVO", "items", items));
    }
    Long producto() {
        return db.queryForObject("""
                INSERT INTO productos(sku,nombre,marca,precio,stock_actual,stock_minimo)
                VALUES (?,'Entrega IT','Marca IT',10.01,10,0) RETURNING id
                """, Long.class, UUID.randomUUID().toString());
    }
    int stock(long id) { return db.queryForObject("SELECT stock_actual FROM productos WHERE id=?", Integer.class, id); }
    int movimientos(long id, String tipo) {
        return db.queryForObject("SELECT count(*) FROM movimientos_stock WHERE venta_id=? AND tipo=?", Integer.class, id, tipo);
    }
    long secuenciaFactura() { return db.queryForObject("SELECT last_value FROM numero_factura_seq", Long.class); }
    ResponseEntity<JsonNode> post(long id, String accion, Rol rol) { return pedir(HttpMethod.POST, ruta(id, accion), null, rol); }
    ResponseEntity<JsonNode> pedir(HttpMethod metodo, String ruta, Object datos, Rol rol) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (rol != null) {
            var usuario = new Usuario();
            usuario.setEmail("admin@smartstock.com");
            usuario.setRol(rol);
            headers.setBearerAuth(jwt.generar(usuario));
        }
        return http.exchange(ruta, metodo, new HttpEntity<>(datos, headers), JsonNode.class);
    }
}
