package com.smartstock.core.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartstock.core.model.*;
import com.smartstock.core.security.JwtService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "SMARTSTOCK_INTEGRACION", matches = "true")
class PedidoIntegracionTest {
    @Autowired TestRestTemplate http;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    @LocalServerPort int puerto;
    Long cliente;
    Long producto;
    Long otro;

    @BeforeEach void preparar() {
        cliente = db.queryForObject("INSERT INTO clientes(nombre,calidad,clasificacion) VALUES ('Pedido IT','BUENA',4) RETURNING id", Long.class);
        producto = producto(10, 3);
        otro = producto(1, 0);
    }

    @AfterEach void limpiar() {
        db.update("DELETE FROM movimientos_stock WHERE producto_id IN (?,?)", producto, otro);
        db.update("DELETE FROM ventas WHERE cliente_id=?", cliente);
        db.update("DELETE FROM productos WHERE id IN (?,?)", producto, otro);
        db.update("DELETE FROM clientes WHERE id=?", cliente);
    }

    @Test void creaSinStockAgrupaRepetidosYConservaPrecioPactado() {
        var datos = datos(List.of(item(producto, 2), item(producto, 5)));
        datos.put("descuentoPct", 12.5);
        datos.put("fechaEnvio", hoy().plusDays(2).toString());
        var creada = post("/api/pedidos", datos, Rol.VENDEDOR);
        assertEquals(201, creada.getStatusCode().value(), creada.toString());
        var pedido = creada.getBody();
        assertEquals("PENDIENTE", pedido.get("estado").asText());
        assertEquals("PACTADA", pedido.get("estadoEntrega").asText());
        assertTrue(pedido.get("numeroOrden").asText().matches("OC-\\d{4}-\\d{6,}"));
        assertTrue(pedido.get("numeroFactura").isNull());
        assertEquals(1, pedido.get("items").size());
        assertEquals(7, pedido.get("items").get(0).get("cantidad").asInt());
        assertEquals(70.07, pedido.get("subtotal").asDouble());
        assertEquals(61.31, pedido.get("total").asDouble());
        assertEquals(8.76, pedido.get("importeDescuento").asDouble());
        assertTrue(pedido.get("conDescuento").asBoolean());
        assertEquals(10, stock(producto));
        assertEquals(0, movimientos());
        db.update("UPDATE productos SET precio=99 WHERE id=?", producto);
        long id = pedido.get("id").asLong();
        var confirmada = post("/api/pedidos/" + id + "/confirmar", null, Rol.VENDEDOR);
        assertEquals(200, confirmada.getStatusCode().value(), confirmada.toString());
        assertEquals(10.01, confirmada.getBody().get("items").get(0).get("precioUnitario").asDouble());
        assertEquals(61.31, confirmada.getBody().get("total").asDouble());
        assertEquals(1, confirmada.getBody().get("advertencias").size());
        assertEquals(3, stock(producto));
        assertEquals(1, movimientos());
        assertEquals(400, post("/api/pedidos/" + id + "/confirmar", null, Rol.ADMIN).getStatusCode().value());
        assertEquals(3, stock(producto));
        assertEquals(403, post("/api/ventas/" + id + "/anular", null, Rol.VENDEDOR).getStatusCode().value());
    }

    @Test void pendienteNoApareceComoCompraNiVentaDelDia() {
        long id = crear(List.of(item(producto, 1))).get("id").asLong();
        assertTrue(get("/api/clientes/" + cliente + "/productos").getBody().isEmpty());
        for (var venta : get("/api/ventas/hoy").getBody()) {
            assertNotEquals(id, venta.get("id").asLong());
        }
        assertEquals(200, post("/api/pedidos/" + id + "/confirmar", null, Rol.VENDEDOR).getStatusCode().value());
        assertEquals(1, get("/api/clientes/" + cliente + "/productos").getBody().get(0).get("unidadesCompradas").asInt());
    }

    @Test void descuentoMaximoYFechaDeHoyConservanIdentidadMonetaria() {
        var datos = datos(List.of(item(producto, 1)));
        datos.put("descuentoPct", 20);
        datos.put("fechaEnvio", hoy().toString());
        var creada = post("/api/pedidos", datos, Rol.ADMIN);
        assertEquals(201, creada.getStatusCode().value());
        var guardada = get("/api/pedidos/" + creada.getBody().get("id").asLong()).getBody();
        assertEquals(8.01, guardada.get("total").asDouble());
        assertEquals(2.00, guardada.get("importeDescuento").asDouble());
        assertEquals(10.01, guardada.get("subtotal").asDouble());
        assertEquals(hoy().toString(), guardada.get("fechaEnvio").asText());
        assertEquals("PACTADA", guardada.get("estadoEntrega").asText());
    }

    @Test void importeFueraDeCapacidadDevuelve400SinReservarStock() {
        db.update("UPDATE productos SET precio=9999999999.99 WHERE id=?", producto);
        assertEquals(400, post("/api/pedidos", datos(List.of(item(producto, 2))), Rol.ADMIN).getStatusCode().value());
        assertEquals(400, post("/api/ventas", Map.of("items", List.of(item(producto, 2))), Rol.ADMIN).getStatusCode().value());
        assertEquals(10, stock(producto));
        assertEquals(0, movimientos());
    }

    @Test void faltaStockRevierteTodosLosProductosYEstado() {
        var pedido = crear(List.of(item(producto, 2), item(otro, 2)));
        long id = pedido.get("id").asLong();
        assertEquals(400, post("/api/pedidos/" + id + "/confirmar", null, Rol.ADMIN).getStatusCode().value());
        assertEquals(10, stock(producto));
        assertEquals(1, stock(otro));
        assertEquals(0, movimientos());
        assertEquals("PENDIENTE", get("/api/pedidos/" + id).getBody().get("estado").asText());
        assertEquals(200, post("/api/ventas/" + id + "/anular", null, Rol.ADMIN).getStatusCode().value());
        assertEquals("CANCELADO", get("/api/pedidos/" + id).getBody().get("estado").asText());
        assertEquals(10, stock(producto));
    }

    @Test void confirmarConcurrentementeDescuentaUnaSolaVez() throws Exception {
        long id = crear(List.of(item(producto, 2))).get("id").asLong();
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Integer> confirmar = () -> {
                inicio.await();
                return post("/api/pedidos/" + id + "/confirmar", null, Rol.VENDEDOR).getStatusCode().value();
            };
            var primera = ejecutor.submit(confirmar);
            var segunda = ejecutor.submit(confirmar);
            inicio.countDown();
            var estados = new ArrayList<>(List.of(primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS)));
            Collections.sort(estados);
            assertEquals(List.of(200, 400), estados);
        }
        assertEquals(8, stock(producto));
        assertEquals(1, movimientos());
    }

    @Test void validaEntradasClienteProductoFechasYDescuentos() {
        for (Object descuento : List.of(-1, 21, "no")) {
            var datos = datos(List.of(item(producto, 1)));
            datos.put("descuentoPct", descuento);
            assertEquals(400, post("/api/pedidos", datos, Rol.ADMIN).getStatusCode().value());
        }
        for (Object cantidad : List.of(0, -1, 1.5, "no")) {
            assertEquals(400, post("/api/pedidos", datos(List.of(item(producto, cantidad))), Rol.ADMIN).getStatusCode().value());
        }
        var datos = datos(List.of(item(producto, 1)));
        datos.put("fechaEnvio", hoy().minusDays(1).toString());
        assertEquals(400, post("/api/pedidos", datos, Rol.ADMIN).getStatusCode().value());
        datos.put("fechaEnvio", "2026-02-30");
        assertEquals(400, post("/api/pedidos", datos, Rol.ADMIN).getStatusCode().value());
        datos.remove("fechaEnvio"); datos.remove("clienteId");
        assertEquals(400, post("/api/pedidos", datos, Rol.ADMIN).getStatusCode().value());
        datos.put("clienteId", Long.MAX_VALUE);
        assertEquals(404, post("/api/pedidos", datos, Rol.ADMIN).getStatusCode().value());
        assertEquals(400, post("/api/pedidos", datos(List.of()), Rol.ADMIN).getStatusCode().value());
        assertEquals(400, post("/api/pedidos", datos(List.of(item(producto, Integer.MAX_VALUE), item(producto, 1))), Rol.ADMIN).getStatusCode().value());
        db.update("UPDATE productos SET activo=false WHERE id=?", producto);
        assertEquals(404, post("/api/pedidos", datos(List.of(item(producto, 1))), Rol.ADMIN).getStatusCode().value());
        assertEquals(404, post("/api/pedidos", datos(List.of(item(Long.MAX_VALUE, 1))), Rol.ADMIN).getStatusCode().value());
        db.update("UPDATE clientes SET activo=false WHERE id=?", cliente);
        assertEquals(404, post("/api/pedidos", datos(List.of(item(otro, 1))), Rol.ADMIN).getStatusCode().value());
        assertEquals(10, stock(producto));
        for (Object forma : List.of("OTRA", 0)) {
            var invalido = datos(List.of(item(otro, 1)));
            invalido.put("formaPago", forma);
            assertEquals(400, post("/api/pedidos", invalido, Rol.ADMIN).getStatusCode().value());
        }
        var decimal = datos(List.of(item(otro, 1)));
        decimal.put("clienteId", 1.5);
        assertEquals(400, post("/api/pedidos", decimal, Rol.ADMIN).getStatusCode().value());
        var nulo = datos(Arrays.asList((Map<String, Object>) null));
        assertEquals(400, post("/api/pedidos", nulo, Rol.ADMIN).getStatusCode().value());
    }

    @Test void listaFiltraPaginaYRestringeMetodos() {
        var primero = crear(List.of(item(producto, 1)));
        long id = primero.get("id").asLong();
        assertEquals("SIN_PACTAR", primero.get("estadoEntrega").asText());
        assertFalse(primero.get("conDescuento").asBoolean());
        crear(List.of(item(producto, 1)));
        String base = "/api/pedidos?clienteId=" + cliente;
        var pagina = get(base + "&size=1").getBody();
        assertEquals(2, pagina.get("totalElementos").asInt());
        assertEquals(2, pagina.get("totalPaginas").asInt());
        assertEquals(1, pagina.get("contenido").size());
        assertTrue(get(base + "&page=3").getBody().get("contenido").isEmpty());
        assertEquals(1, get(base + "&numeroOrden=" + primero.get("numeroOrden").asText()).getBody().get("contenido").size());
        assertEquals(2, get(base + "&desde=" + hoy() + "&hasta=" + hoy() + "&estado=PENDIENTE").getBody().get("totalElementos").asInt());
        assertEquals(0, get(base + "&estado=CONFIRMADO").getBody().get("totalElementos").asInt());
        for (String filtro : List.of("page=-1", "size=101", "size=0", "estado=OTRO", "desde=2026-02-30",
                "desde=2026-11-02&hasta=2026-11-01", "page=2147483647&size=100")) {
            assertEquals(400, get(base + "&" + filtro).getStatusCode().value(), filtro);
        }
        assertEquals(404, get("/api/pedidos/" + Long.MAX_VALUE).getStatusCode().value());
        assertEquals(401, post("/api/pedidos", datos(List.of(item(producto, 1))), null).getStatusCode().value());
        for (String estado : List.of("ENVIADO", "ENTREGADO", "CANCELADO")) {
            db.update("UPDATE ventas SET estado=?,anulada=? WHERE id=?", estado, estado.equals("CANCELADO"), id);
            assertEquals(400, post("/api/pedidos/" + id + "/confirmar", null, Rol.ADMIN).getStatusCode().value());
            assertEquals(400, post("/api/ventas/" + id + "/anular", null, Rol.ADMIN).getStatusCode().value());
        }
    }

    @Test void ventasLegacyRecuperanConsumidorFinalSiFueRenombrado() {
        Long original = db.queryForObject("SELECT id FROM clientes WHERE nombre='Consumidor final' AND activo ORDER BY id LIMIT 1", Long.class);
        String nombreTemporal = "Consumidor IT-" + UUID.randomUUID();
        db.update("UPDATE clientes SET nombre=? WHERE id=?", nombreTemporal, original);
        Long nuevo = null;
        try {
            var respuesta = post("/api/ventas", Map.of("items", List.of(item(producto, 1))), Rol.VENDEDOR);
            assertEquals(201, respuesta.getStatusCode().value(), respuesta.toString());
            nuevo = respuesta.getBody().get("clienteId").asLong();
            assertNotEquals(original, nuevo);
            assertEquals("Consumidor final", respuesta.getBody().get("cliente").asText());
            db.update("UPDATE ventas SET cliente_id=? WHERE id=?", cliente, respuesta.getBody().get("id").asLong());
        } finally {
            if (nuevo != null) db.update("DELETE FROM clientes WHERE id=?", nuevo);
            db.update("UPDATE clientes SET nombre='Consumidor final' WHERE id=?", original);
        }
    }

    @Test void pedidosConcurrentesTienenNumerosUnicosSinDescontarStock() throws Exception {
        try (var ejecutor = Executors.newFixedThreadPool(4)) {
            var inicio = new CountDownLatch(1);
            var tareas = new ArrayList<Future<String>>();
            for (int i = 0; i < 4; i++) {
                tareas.add(ejecutor.submit(() -> {
                    inicio.await();
                    return crear(List.of(item(producto, 1))).get("numeroOrden").asText();
                }));
            }
            inicio.countDown();
            var numeros = new HashSet<String>();
            for (var tarea : tareas) numeros.add(tarea.get(20, TimeUnit.SECONDS));
            assertEquals(4, numeros.size());
        }
        assertEquals(10, stock(producto));
        assertEquals(0, movimientos());
    }

    @Test void confirmacionNoDependeDelEstadoActualDelClienteYConservaMarca() {
        long id = crear(List.of(item(producto, 1))).get("id").asLong();
        db.update("UPDATE clientes SET activo=false WHERE id=?", cliente);
        var respuesta = post("/api/pedidos/" + id + "/confirmar", null, Rol.ADMIN);
        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals("Marca IT", respuesta.getBody().get("items").get(0).get("marca").asText());
        assertEquals("Pedido IT", respuesta.getBody().get("cliente").asText());
        assertTrue(respuesta.getBody().get("advertencias").isEmpty());
    }

    @Test void productoInactivoNoPuedeConfirmarseYNumeroAusenteEs404() {
        long id = crear(List.of(item(producto, 1))).get("id").asLong();
        db.update("UPDATE productos SET activo=false WHERE id=?", producto);
        assertEquals(404, post("/api/pedidos/" + id + "/confirmar", null, Rol.VENDEDOR).getStatusCode().value());
        assertEquals(10, stock(producto));
        assertEquals(0, movimientos());
        assertEquals(404, post("/api/pedidos/" + Long.MAX_VALUE + "/confirmar", null, Rol.ADMIN).getStatusCode().value());
    }

    @Test void hastaIncluyeUltimoMicrosegundoYExcluyeMedianocheSiguiente() {
        long ultimoDelDia = crear(List.of(item(producto, 1))).get("id").asLong();
        long siguienteDia = crear(List.of(item(producto, 1))).get("id").asLong();
        db.update("UPDATE ventas SET fecha=TIMESTAMP '2026-10-31 23:59:59.999999' WHERE id=?", ultimoDelDia);
        db.update("UPDATE ventas SET fecha=TIMESTAMP '2026-11-01 00:00:00' WHERE id=?", siguienteDia);

        var respuesta = get("/api/pedidos?clienteId=" + cliente + "&hasta=2026-10-31");
        assertEquals(200, respuesta.getStatusCode().value());
        var pagina = respuesta.getBody();
        assertEquals(1, pagina.get("totalElementos").asInt());
        assertEquals(1, pagina.get("contenido").size());
        assertEquals(ultimoDelDia, pagina.get("contenido").get(0).get("id").asLong());
    }

    @Test void hastaMaximoDeLocalDateDevuelve400() {
        var usuario = new Usuario();
        usuario.setEmail("admin@smartstock.com");
        usuario.setRol(Rol.VENDEDOR);
        var headers = new HttpHeaders();
        headers.setBearerAuth(jwt.generar(usuario));
        var uri = URI.create("http://localhost:" + puerto + "/api/pedidos?hasta=%2B999999999-12-31");
        var respuesta = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertEquals(400, respuesta.getStatusCode().value());
        assertEquals("La fecha hasta supera el máximo permitido", respuesta.getBody().get("error").asText());
    }

    @Test void ventaLegacyEsPedidoConfirmadoConClientePredeterminado() {
        var respuesta = post("/api/ventas", Map.of("items", List.of(item(producto, 2))), Rol.VENDEDOR);
        assertEquals(201, respuesta.getStatusCode().value());
        long id = respuesta.getBody().get("id").asLong();
        // Limpieza propia también para el cliente predeterminado.
        db.update("UPDATE ventas SET cliente_id=? WHERE id=?", cliente, id);
        assertNotNull(respuesta.getBody().get("clienteId"));
        assertEquals("Consumidor final", respuesta.getBody().get("cliente").asText());
        var pedido = get("/api/pedidos/" + id).getBody();
        assertEquals("CONFIRMADO", pedido.get("estado").asText());
        assertEquals("EFECTIVO", pedido.get("formaPago").asText());
        assertEquals(8, stock(producto));
        assertEquals(200, post("/api/ventas/" + id + "/anular", null, Rol.ADMIN).getStatusCode().value());
        assertEquals("CANCELADO", get("/api/pedidos/" + id).getBody().get("estado").asText());
        assertEquals(10, stock(producto));
    }

    JsonNode crear(List<Map<String, Object>> items) {
        var r = post("/api/pedidos", datos(items), Rol.VENDEDOR);
        assertEquals(201, r.getStatusCode().value(), r.toString());
        return r.getBody();
    }
    Map<String, Object> datos(List<Map<String, Object>> items) {
        return new HashMap<>(Map.of("clienteId", cliente, "formaPago", "EFECTIVO", "items", items));
    }
    Map<String, Object> item(Long id, Object cantidad) { return Map.of("productoId", id, "cantidad", cantidad); }
    LocalDate hoy() { return LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")); }
    Long producto(int stock, int minimo) {
        return db.queryForObject("""
                INSERT INTO productos(sku,nombre,marca,precio,stock_actual,stock_minimo)
                VALUES (?,'Pedido IT','Marca IT',10.01,?,?) RETURNING id
                """, Long.class, UUID.randomUUID().toString(), stock, minimo);
    }
    int stock(Long id) { return db.queryForObject("SELECT stock_actual FROM productos WHERE id=?", Integer.class, id); }
    int movimientos() { return db.queryForObject("SELECT count(*) FROM movimientos_stock WHERE producto_id IN (?,?)", Integer.class, producto, otro); }
    ResponseEntity<JsonNode> get(String ruta) { return pedir(HttpMethod.GET, ruta, null, Rol.VENDEDOR); }
    ResponseEntity<JsonNode> post(String ruta, Object datos, Rol rol) { return pedir(HttpMethod.POST, ruta, datos, rol); }
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
