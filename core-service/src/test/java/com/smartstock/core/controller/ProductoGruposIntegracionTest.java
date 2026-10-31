package com.smartstock.core.controller;

import static org.junit.jupiter.api.Assertions.*;
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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="SMARTSTOCK_INTEGRACION",matches="true")
class ProductoGruposIntegracionTest {
    @Autowired TestRestTemplate http;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    Long productoId;
    Long grupoId;
    @AfterEach void limpiar() {
        if(productoId!=null) db.update("DELETE FROM productos WHERE id=?",productoId);
        if(grupoId!=null) db.update("DELETE FROM categorias WHERE id=?",grupoId);
    }
    @Test void productoExponeMarcaProveedorGrupoYMinimoPorDefecto() {
        Long proveedor=db.queryForObject("SELECT MIN(id) FROM proveedores WHERE activo",Long.class);
        Long grupo=db.queryForObject("SELECT MIN(id) FROM categorias",Long.class);
        var datos=new HashMap<String,Object>();
        datos.put("sku","IT-"+UUID.randomUUID());datos.put("nombre","Producto IT");datos.put("categoriaId",grupo);
        datos.put("marca","Marca IT");datos.put("proveedorId",proveedor);datos.put("precio",10);datos.put("costo",5);datos.put("stockActual",30);
        var respuesta=pedir(HttpMethod.POST,"/api/productos",datos,Rol.ADMIN);
        assertEquals(201,respuesta.getStatusCode().value());
        productoId=respuesta.getBody().get("id").asLong();
        assertEquals(30,respuesta.getBody().get("stockMinimo").asInt());
        assertEquals("Marca IT",respuesta.getBody().get("marca").asText());
        assertEquals(proveedor.longValue(),respuesta.getBody().get("proveedorId").asLong());
        assertEquals("BAJO_MINIMO",respuesta.getBody().get("estadoStock").asText());
        assertEquals(respuesta.getBody().get("categoriaNombre"),respuesta.getBody().get("grupo"));
        var lista=pedir(HttpMethod.GET,"/api/productos?proveedorId="+proveedor,null,Rol.VENDEDOR).getBody();
        assertFalse(lista.isEmpty());
        assertTrue(java.util.stream.StreamSupport.stream(lista.spliterator(),false)
                .anyMatch(p -> p.get("id").asLong()==productoId));
        for(JsonNode p:lista) assertEquals(proveedor.longValue(),p.get("proveedorId").asLong());
        datos.put("stockActual",29);datos.put("stockMinimo",30);
        assertEquals("BAJO_MINIMO",pedir(HttpMethod.PUT,"/api/productos/"+productoId,datos,Rol.ADMIN).getBody().get("estadoStock").asText());
        datos.put("proveedorId",Long.MAX_VALUE);
        assertEquals(404,pedir(HttpMethod.PUT,"/api/productos/"+productoId,datos,Rol.ADMIN).getStatusCode().value());
    }
    @Test void putAnteriorConservaMarcaYProveedorYPermiteVaciarMarca() {
        Long proveedor=db.queryForObject("SELECT MIN(id) FROM proveedores WHERE activo",Long.class);
        var datos=new HashMap<String,Object>(Map.of("sku","IT-"+UUID.randomUUID(),"nombre","Producto IT","marca","Marca IT",
                "proveedorId",proveedor,"precio",10,"costo",5,"stockActual",40,"stockMinimo",30));
        var creado=pedir(HttpMethod.POST,"/api/productos",datos,Rol.ADMIN);
        assertEquals(201,creado.getStatusCode().value());productoId=creado.getBody().get("id").asLong();
        datos.remove("marca");datos.remove("proveedorId");datos.put("precio",12);
        var editado=pedir(HttpMethod.PUT,"/api/productos/"+productoId,datos,Rol.ADMIN);
        assertEquals(200,editado.getStatusCode().value());
        assertEquals("Marca IT",editado.getBody().get("marca").asText());
        assertEquals(proveedor.longValue(),editado.getBody().get("proveedorId").asLong());
        datos.put("marca","");
        var sinMarca=pedir(HttpMethod.PUT,"/api/productos/"+productoId,datos,Rol.ADMIN);
        assertTrue(sinMarca.getBody().get("marca").isNull());
        assertEquals(proveedor.longValue(),sinMarca.getBody().get("proveedorId").asLong());
    }
    @Test void grupoPermiteEditarReglasYValidaLimitesYRol() {
        String nombre="Grupo IT "+UUID.randomUUID();
        var creado=pedir(HttpMethod.POST,"/api/categorias",Map.of("nombre",nombre),Rol.ADMIN);
        assertEquals(201,creado.getStatusCode().value());grupoId=creado.getBody().get("id").asLong();
        assertTrue(creado.getBody().hasNonNull("diasAlertaVencimiento"), "El contrato debe incluir días de alerta");
        assertEquals(60,creado.getBody().get("diasAlertaVencimiento").asInt());
        var datos=new HashMap<String,Object>(Map.of("nombre",nombre,"diasAlertaVencimiento",15,"umbralRotacion",0.2));
        var editado=pedir(HttpMethod.PUT,"/api/categorias/"+grupoId,datos,Rol.ADMIN);
        assertEquals(200,editado.getStatusCode().value());assertEquals(15,editado.getBody().get("diasAlertaVencimiento").asInt());
        assertEquals(0.2,editado.getBody().get("umbralRotacion").asDouble());
        datos.put("umbralRotacion",new java.math.BigDecimal("0.00001"));
        assertEquals(200,pedir(HttpMethod.PUT,"/api/categorias/"+grupoId,datos,Rol.ADMIN).getStatusCode().value());
        var grupos=pedir(HttpMethod.GET,"/api/categorias",null,Rol.VENDEDOR).getBody();
        JsonNode encontrado=null;
        for(JsonNode g:grupos) if(g.get("id").asLong()==grupoId) encontrado=g;
        assertNotNull(encontrado,"El grupo editado debe aparecer en la consulta");
        assertEquals(0,new java.math.BigDecimal("0.00001").compareTo(encontrado.get("umbralRotacion").decimalValue()));
        datos.put("nombre","Almacén");
        assertEquals(400,pedir(HttpMethod.PUT,"/api/categorias/"+grupoId,datos,Rol.ADMIN).getStatusCode().value());
        datos.put("nombre",nombre);
        assertEquals(403,pedir(HttpMethod.PUT,"/api/categorias/"+grupoId,datos,Rol.VENDEDOR).getStatusCode().value());
        datos.put("diasAlertaVencimiento",0);assertEquals(400,pedir(HttpMethod.PUT,"/api/categorias/"+grupoId,datos,Rol.ADMIN).getStatusCode().value());
        datos.put("diasAlertaVencimiento",15);datos.put("umbralRotacion",1.1);
        assertEquals(400,pedir(HttpMethod.PUT,"/api/categorias/"+grupoId,datos,Rol.ADMIN).getStatusCode().value());
        datos.put("umbralRotacion",0.1);assertEquals(404,pedir(HttpMethod.PUT,"/api/categorias/9223372036854775807",datos,Rol.ADMIN).getStatusCode().value());
    }
    ResponseEntity<JsonNode> pedir(HttpMethod metodo,String ruta,Object datos,Rol rol) {
        Usuario u=new Usuario();u.setEmail("it@example.com");u.setNombre("IT");u.setRol(rol);
        HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.setBearerAuth(jwt.generar(u));
        return http.exchange(ruta,metodo,new HttpEntity<>(datos,h),JsonNode.class);
    }
}
