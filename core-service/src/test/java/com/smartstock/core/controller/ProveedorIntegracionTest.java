package com.smartstock.core.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class ProveedorIntegracionTest {
    @Autowired TestRestTemplate http;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    Long id;
    Long productoId;
    String nombre = "ProveedorIT-" + UUID.randomUUID();

    @AfterEach void limpiar() {
        if (productoId != null) db.update("DELETE FROM productos WHERE id=?", productoId);
        if (id != null) db.update("DELETE FROM proveedores WHERE id=?", id);
    }

    @Test void crudBajaLogicaYProductosDelProveedor() {
        crear();
        assertEquals(nombre, pedir(HttpMethod.GET,"/api/proveedores/"+id,null,Rol.VENDEDOR).getBody().get("nombre").asText());
        assertTrue(pedir(HttpMethod.GET,"/api/proveedores?q="+nombre.replace(" ","%20"),null,Rol.VENDEDOR).getBody().size()>0);
        productoId=db.queryForObject("INSERT INTO productos(sku,nombre,marca,proveedor_id,precio) VALUES (?,'Producto IT','Marca IT',?,10) RETURNING id",Long.class,"IT-"+UUID.randomUUID(),id);
        var productos=pedir(HttpMethod.GET,"/api/proveedores/"+id+"/productos",null,Rol.VENDEDOR);
        assertEquals(200,productos.getStatusCode().value());
        assertEquals("Marca IT",productos.getBody().get(0).get("marca").asText());
        db.update("UPDATE productos SET activo=false WHERE id=?",productoId);
        assertTrue(pedir(HttpMethod.GET,"/api/proveedores/"+id+"/productos",null,Rol.ADMIN).getBody().isEmpty());
        var editado=pedir(HttpMethod.PUT,"/api/proveedores/"+id,datos(12,5,"REGULAR"),Rol.ADMIN);
        assertEquals(200,editado.getStatusCode().value());
        assertEquals(12,editado.getBody().get("leadTimeDias").asInt());
        assertEquals(204,pedir(HttpMethod.DELETE,"/api/proveedores/"+id,null,Rol.ADMIN).getStatusCode().value());
        assertFalse(db.queryForObject("SELECT activo FROM proveedores WHERE id=?",Boolean.class,id));
        assertEquals(404,pedir(HttpMethod.GET,"/api/proveedores/"+id,null,Rol.ADMIN).getStatusCode().value());
        assertEquals(404,pedir(HttpMethod.GET,"/api/proveedores/"+id+"/productos",null,Rol.ADMIN).getStatusCode().value());
    }

    @Test
    void productosIncluyenCategoriaYStockSinPerderCamposOriginales() {
        crear();
        String sku = "IT-" + UUID.randomUUID();
        Long categoriaId = db.queryForObject("SELECT id FROM categorias ORDER BY id LIMIT 1", Long.class);
        String categoriaNombre = db.queryForObject("SELECT nombre FROM categorias WHERE id=?", String.class, categoriaId);
        productoId = db.queryForObject(
                "INSERT INTO productos(sku,nombre,marca,proveedor_id,categoria_id,precio,stock_actual,stock_minimo) "
                        + "VALUES (?,'Producto con stock','Marca IT',?,?,10,5,30) RETURNING id",
                Long.class, sku, id, categoriaId);

        var respuesta = pedir(HttpMethod.GET, "/api/proveedores/" + id + "/productos", null, Rol.VENDEDOR);
        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals(1, respuesta.getBody().size());
        JsonNode producto = respuesta.getBody().get(0);
        assertEquals(productoId.longValue(), producto.path("id").asLong());
        assertEquals(sku, producto.path("sku").asText());
        assertEquals("Producto con stock", producto.path("nombre").asText());
        assertEquals("Marca IT", producto.path("marca").asText());
        assertEquals(categoriaNombre, producto.path("categoriaNombre").asText());
        assertTrue(producto.path("stockActual").isInt());
        assertEquals(5, producto.path("stockActual").asInt());
        assertTrue(producto.path("stockMinimo").isInt());
        assertEquals(30, producto.path("stockMinimo").asInt());

        db.update("UPDATE productos SET categoria_id=null WHERE id=?", productoId);
        JsonNode sinCategoria = pedir(HttpMethod.GET, "/api/proveedores/" + id + "/productos", null, Rol.ADMIN)
                .getBody().get(0);
        assertTrue(sinCategoria.has("categoriaNombre"));
        assertTrue(sinCategoria.get("categoriaNombre").isNull());
        assertEquals(5, sinCategoria.path("stockActual").asInt());
        assertEquals(30, sinCategoria.path("stockMinimo").asInt());
    }

    @ParameterizedTest @ValueSource(strings={"{\"leadTimeDias\":0,\"clasificacion\":3,\"calidad\":\"BUENA\"}","{\"leadTimeDias\":2,\"clasificacion\":6,\"calidad\":\"BUENA\"}","{\"leadTimeDias\":2,\"clasificacion\":0,\"calidad\":\"BUENA\"}","{\"leadTimeDias\":2,\"clasificacion\":3,\"calidad\":\"DESCONOCIDA\"}"})
    void validaRangosYCalidadConErrorJson(String datos) {
        String cuerpo=datos.replace("{","{\"nombre\":\"Proveedor\",");
        var respuesta=pedir(HttpMethod.POST,"/api/proveedores",cuerpo,Rol.ADMIN);
        assertEquals(400,respuesta.getStatusCode().value());
        assertTrue(respuesta.getBody().hasNonNull("error"));
    }

    @Test void vendedorSoloConsultaYAnonimoRecibe401() {
        assertEquals(403,pedir(HttpMethod.POST,"/api/proveedores",datos(3,2,"MALA"),Rol.VENDEDOR).getStatusCode().value());
        assertEquals(403,pedir(HttpMethod.PUT,"/api/proveedores/99",datos(3,2,"MALA"),Rol.VENDEDOR).getStatusCode().value());
        assertEquals(403,pedir(HttpMethod.DELETE,"/api/proveedores/99",null,Rol.VENDEDOR).getStatusCode().value());
        assertEquals(401,pedir(HttpMethod.GET,"/api/proveedores",null,null).getStatusCode().value());
        assertEquals(404,pedir(HttpMethod.GET,"/api/proveedores/9223372036854775807",null,Rol.ADMIN).getStatusCode().value());
    }

    @Test void rechazaCalidadNumericaYDevuelveLongitudesEnEspanol() {
        var numerica=pedir(HttpMethod.POST,"/api/proveedores","{\"nombre\":\"Proveedor\",\"leadTimeDias\":2,\"clasificacion\":3,\"calidad\":0}",Rol.ADMIN);
        // Si la API aceptara el dato, conservar el id para limpiar la prueba igualmente.
        if(numerica.getStatusCode().value()==201) id=numerica.getBody().get("id").asLong();
        assertEquals(400,numerica.getStatusCode().value());
        var cuerpo=new java.util.HashMap<>(datos(2,3,"BUENA"));
        cuerpo.put("direccion","a".repeat(256));
        var largo=pedir(HttpMethod.POST,"/api/proveedores",cuerpo,Rol.ADMIN);
        assertEquals(400,largo.getStatusCode().value());
        assertTrue(largo.getBody().get("error").asText().contains("La dirección admite hasta 255 caracteres"));
    }

    void crear() {
        var respuesta=pedir(HttpMethod.POST,"/api/proveedores",datos(5,4,"BUENA"),Rol.ADMIN);
        assertEquals(201,respuesta.getStatusCode().value());
        id=respuesta.getBody().get("id").asLong();
    }
    Map<String,Object> datos(int dias,int estrellas,String calidad) {
        return Map.of("nombre",nombre,"direccion","Calle 1","telefono","111","leadTimeDias",dias,"clasificacion",estrellas,"calidad",calidad);
    }
    ResponseEntity<JsonNode> pedir(HttpMethod metodo,String ruta,Object datos,Rol rol) {
        HttpHeaders headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if(rol!=null) { Usuario usuario=new Usuario();usuario.setEmail("it@example.com");usuario.setNombre("IT");usuario.setRol(rol);headers.setBearerAuth(jwt.generar(usuario)); }
        return http.exchange(ruta,metodo,new HttpEntity<>(datos,headers),JsonNode.class);
    }
}
