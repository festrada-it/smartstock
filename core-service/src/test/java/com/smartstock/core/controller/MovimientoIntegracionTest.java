package com.smartstock.core.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true",
        "spring.jpa.show-sql=true"
})
@EnabledIfEnvironmentVariable(named = "SMARTSTOCK_INTEGRACION", matches = "true")
class MovimientoIntegracionTest {
    @Autowired TestRestTemplate http;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    Long productoId;

    @BeforeEach void crearProducto() {
        productoId = db.queryForObject("INSERT INTO productos(sku,nombre,precio) VALUES (?,'Historial IT',10) RETURNING id",
                Long.class, "HIST-" + UUID.randomUUID());
    }

    @AfterEach void limpiar() {
        db.update("DELETE FROM movimientos_stock WHERE producto_id=?", productoId);
        db.update("DELETE FROM productos WHERE id=?", productoId);
    }

    @Test void paginaEnBaseConOrdenPorFechaEIdYConservaAuditoriaDeInactivos() {
        Long antiguo = movimiento("2026-10-01 10:00:00", "COMPRA", 5);
        Long empateMenor = movimiento("2026-10-03 10:00:00", "VENTA", -2);
        Long intermedio = movimiento("2026-10-02 10:00:00", "AJUSTE", 1);
        Long empateMayor = movimiento("2026-10-03 10:00:00", "COMPRA", 3);
        db.update("UPDATE productos SET activo=false WHERE id=?", productoId);

        var primera = pedir(productoId, "?page=0&size=2", Rol.ADMIN);
        assertEquals(200, primera.getStatusCode().value());
        var cuerpo = primera.getBody();
        assertTrue(cuerpo.isObject(), "El historial debe devolver un envelope paginado");
        assertEquals(5, cuerpo.size());
        assertEquals(0, cuerpo.get("pagina").asInt());
        assertEquals(2, cuerpo.get("tamanio").asInt());
        assertEquals(4, cuerpo.get("totalElementos").asInt());
        assertEquals(2, cuerpo.get("totalPaginas").asInt());
        assertIds(cuerpo, empateMayor, empateMenor);
        var movimiento = cuerpo.get("contenido").get(1);
        assertEquals(10, movimiento.size());
        assertTrue(movimiento.get("loteId").isNull());
        assertEquals(productoId.longValue(), movimiento.get("productoId").asLong());
        assertEquals("VENTA", movimiento.get("tipo").asText());
        assertEquals(-2, movimiento.get("cantidad").asInt());
        assertEquals("2026-10-03T10:00:00", movimiento.get("fecha").asText());
        assertTrue(movimiento.get("usuarioId").isNull());
        assertTrue(movimiento.get("usuario").isNull());
        assertTrue(movimiento.get("ventaId").isNull());
        assertEquals("Prueba historial", movimiento.get("observacion").asText());
        assertIds(pedir(productoId, "?page=1&size=2", Rol.VENDEDOR).getBody(), intermedio, antiguo);
        var fuera = pedir(productoId, "?page=2&size=2", Rol.ADMIN).getBody();
        assertTrue(fuera.get("contenido").isEmpty());
        assertEquals(4, fuera.get("totalElementos").asInt());
    }

    @Test void sinMovimientosDevuelveEnvelopeVacioYValoresPorDefecto() {
        var respuesta = pedir(productoId, "", Rol.VENDEDOR);
        assertEquals(200, respuesta.getStatusCode().value());
        var cuerpo = respuesta.getBody();
        assertTrue(cuerpo.isObject(), "El historial debe devolver un envelope paginado");
        assertTrue(cuerpo.get("contenido").isEmpty());
        assertEquals(0, cuerpo.get("pagina").asInt());
        assertEquals(50, cuerpo.get("tamanio").asInt());
        assertEquals(0, cuerpo.get("totalElementos").asInt());
        assertEquals(0, cuerpo.get("totalPaginas").asInt());
        assertEquals(200, pedir(productoId, "?size=100", Rol.ADMIN).getStatusCode().value());
    }

    @Test void parametrosInvalidosDevuelven400EnEspanolYNoTruncanValores() {
        for (String consulta : List.of("page=-1", "size=0", "size=-1", "size=101",
                "size=abc", "page=abc", "page=1.5", "size=2147483648", "page=2147483648",
                "page=2147483647&size=100")) {
            var respuesta = pedir(productoId, "?" + consulta, Rol.ADMIN);
            assertEquals(400, respuesta.getStatusCode().value(), consulta);
            assertTrue(respuesta.getBody().get("error").asText().startsWith("El parámetro")
                    || respuesta.getBody().get("error").asText().startsWith("La página"), consulta);
        }
    }

    @Test void productoInexistenteDevuelve404YSinToken401() {
        var ausente = pedir(Long.MAX_VALUE, "", Rol.ADMIN);
        assertEquals(404, ausente.getStatusCode().value());
        assertEquals("Producto " + Long.MAX_VALUE + " no encontrado", ausente.getBody().get("error").asText());
        assertEquals(401, pedir(productoId, "", null).getStatusCode().value());
    }

    Long movimiento(String fecha, String tipo, int cantidad) {
        return db.queryForObject("INSERT INTO movimientos_stock(producto_id,tipo,cantidad,fecha,observacion) VALUES (?,?,?,CAST(? AS timestamp),'Prueba historial') RETURNING id",
                Long.class, productoId, tipo, cantidad, fecha);
    }

    void assertIds(JsonNode cuerpo, Long primero, Long segundo) {
        assertEquals(2, cuerpo.get("contenido").size());
        assertEquals(primero.longValue(), cuerpo.get("contenido").get(0).get("id").asLong());
        assertEquals(segundo.longValue(), cuerpo.get("contenido").get(1).get("id").asLong());
    }

    ResponseEntity<JsonNode> pedir(Long id, String consulta, Rol rol) {
        HttpHeaders headers = new HttpHeaders();
        if (rol != null) {
            Usuario usuario = new Usuario();
            usuario.setEmail("admin@smartstock.com");
            usuario.setNombre("IT");
            usuario.setRol(rol);
            headers.setBearerAuth(jwt.generar(usuario));
        }
        return http.exchange("/api/productos/" + id + "/movimientos" + consulta, HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
    }
}
