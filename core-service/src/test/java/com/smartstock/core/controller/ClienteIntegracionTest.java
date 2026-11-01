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
class ClienteIntegracionTest {
    @Autowired TestRestTemplate http;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    Long id;
    Long productoId;
    String nombre="ClienteIT-"+UUID.randomUUID();
    @AfterEach void limpiar() {
        if(productoId!=null) {
            db.update("DELETE FROM movimientos_stock WHERE producto_id=?",productoId);
            db.update("DELETE FROM ventas WHERE id IN (SELECT venta_id FROM detalle_venta WHERE producto_id=?)",productoId);
            db.update("DELETE FROM productos WHERE id=?",productoId);
        }
        if(id!=null) db.update("DELETE FROM clientes WHERE id=?",id);
    }
    @Test void crudYPermisosConBajaLogica() {
        crear();
        assertEquals(nombre,pedir(HttpMethod.GET,"/api/clientes/"+id,null,Rol.VENDEDOR).getBody().get("nombre").asText());
        assertEquals(1,pedir(HttpMethod.GET,"/api/clientes?q="+nombre,null,Rol.VENDEDOR).getBody().size());
        var nuevo=new HashMap<String,Object>(datos());nuevo.put("clasificacion",2);
        assertEquals(2,pedir(HttpMethod.PUT,"/api/clientes/"+id,nuevo,Rol.ADMIN).getBody().get("clasificacion").asInt());
        assertEquals(403,pedir(HttpMethod.POST,"/api/clientes",datos(),Rol.VENDEDOR).getStatusCode().value());
        assertEquals(403,pedir(HttpMethod.PUT,"/api/clientes/"+id,nuevo,Rol.VENDEDOR).getStatusCode().value());
        assertEquals(403,pedir(HttpMethod.DELETE,"/api/clientes/"+id,null,Rol.VENDEDOR).getStatusCode().value());
        assertEquals(204,pedir(HttpMethod.DELETE,"/api/clientes/"+id,null,Rol.ADMIN).getStatusCode().value());
        assertFalse(db.queryForObject("SELECT activo FROM clientes WHERE id=?",Boolean.class,id));
        assertEquals(404,pedir(HttpMethod.GET,"/api/clientes/"+id,null,Rol.ADMIN).getStatusCode().value());
    }
    @Test void historialPaginadoYProductosSeCalculanDesdeVentasSinAnuladas() {
        crear();producto();
        var venta1=venta(id,2);assertEquals(201,venta1.getStatusCode().value());
        assertTrue(venta1.getBody().hasNonNull("clienteId"),"La venta debe incluir cliente");
        assertEquals(id.longValue(),venta1.getBody().get("clienteId").asLong());
        assertEquals(nombre,venta1.getBody().get("cliente").asText());
        var venta2=venta(id,3);assertEquals(201,venta2.getStatusCode().value());
        Long segunda=venta2.getBody().get("id").asLong();
        assertEquals(200,pedir(HttpMethod.POST,"/api/ventas/"+segunda+"/anular",null,Rol.ADMIN).getStatusCode().value());
        var historial=pedir(HttpMethod.GET,"/api/clientes/"+id+"/pedidos?page=0&size=1",null,Rol.VENDEDOR).getBody();
        assertEquals(2,historial.get("totalElementos").asInt());assertEquals(2,historial.get("totalPaginas").asInt());
        assertEquals(segunda.longValue(),historial.get("contenido").get(0).get("id").asLong());
        assertTrue(historial.get("contenido").get(0).get("anulada").asBoolean());
        db.update("UPDATE productos SET activo=false WHERE id=?",productoId);
        var productos=pedir(HttpMethod.GET,"/api/clientes/"+id+"/productos",null,Rol.VENDEDOR).getBody();
        assertEquals(1,productos.size());assertEquals(2,productos.get(0).get("unidadesCompradas").asInt());
        assertEquals("Marca IT",productos.get(0).get("marca").asText());assertTrue(productos.get(0).hasNonNull("ultimaCompra"));
    }
    @Test void validaCalidadClasificacionYNombre() {
        for(Object calidad:List.of("OTRA",0)) {
            var datos=new HashMap<String,Object>(datos());datos.put("calidad",calidad);
            var r=pedir(HttpMethod.POST,"/api/clientes",datos,Rol.ADMIN);
            assertEquals(400,r.getStatusCode().value());assertTrue(r.getBody().hasNonNull("error"));
        }
        var datos=new HashMap<String,Object>(datos());datos.put("clasificacion",6);
        assertEquals(400,pedir(HttpMethod.POST,"/api/clientes",datos,Rol.ADMIN).getStatusCode().value());
        datos.put("clasificacion",3);datos.put("nombre"," ");
        assertEquals(400,pedir(HttpMethod.POST,"/api/clientes",datos,Rol.ADMIN).getStatusCode().value());
    }
    @Test void ventaSinClienteSigueFuncionandoYClienteInactivoNoDescuentaStock() {
        producto();
        var sinCliente=pedir(HttpMethod.POST,"/api/ventas",
                Map.of("items",List.of(Map.of("productoId",productoId,"cantidad",1))),Rol.VENDEDOR);
        assertEquals(201,sinCliente.getStatusCode().value());
        assertTrue(sinCliente.getBody().get("clienteId").isNull());
        assertTrue(sinCliente.getBody().get("cliente").isNull());
        crear();
        assertEquals(204,pedir(HttpMethod.DELETE,"/api/clientes/"+id,null,Rol.ADMIN).getStatusCode().value());
        assertEquals(404,venta(id,1).getStatusCode().value());
        assertEquals(9,db.queryForObject("SELECT stock_actual FROM productos WHERE id=?",Integer.class,productoId));
    }
    @Test void validaPaginacionYClienteInexistenteSinDescontarStock() {
        crear();producto();
        for(String query:List.of("page=-1","size=0","size=101","size=abc","page=2147483647&size=100")) {
            assertEquals(400,pedir(HttpMethod.GET,"/api/clientes/"+id+"/pedidos?"+query,null,Rol.VENDEDOR).getStatusCode().value());
        }
        assertEquals(404,venta(Long.MAX_VALUE,1).getStatusCode().value());
        assertEquals(10,db.queryForObject("SELECT stock_actual FROM productos WHERE id=?",Integer.class,productoId));
        assertEquals(401,pedir(HttpMethod.GET,"/api/clientes",null,null).getStatusCode().value());
        assertTrue(pedir(HttpMethod.GET,"/api/clientes/"+id+"/pedidos?page=10",null,Rol.VENDEDOR).getBody().get("contenido").isEmpty());
    }
    void crear() { var r=pedir(HttpMethod.POST,"/api/clientes",datos(),Rol.ADMIN);assertEquals(201,r.getStatusCode().value());id=r.getBody().get("id").asLong(); }
    void producto() { productoId=db.queryForObject("INSERT INTO productos(sku,nombre,marca,precio,stock_actual) VALUES (?,'Producto IT','Marca IT',10,10) RETURNING id",Long.class,"IT-"+UUID.randomUUID()); }
    Map<String,Object> datos() { return Map.of("nombre",nombre,"calidad","BUENA","clasificacion",4); }
    ResponseEntity<JsonNode> venta(Long cliente,int cantidad) { return pedir(HttpMethod.POST,"/api/ventas",Map.of("clienteId",cliente,"items",List.of(Map.of("productoId",productoId,"cantidad",cantidad))),Rol.VENDEDOR); }
    ResponseEntity<JsonNode> pedir(HttpMethod metodo,String ruta,Object datos,Rol rol) {
        HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);
        if(rol!=null) { Usuario u=new Usuario();u.setEmail("admin@smartstock.com");u.setNombre("IT");u.setRol(rol);h.setBearerAuth(jwt.generar(u)); }
        return http.exchange(ruta,metodo,new HttpEntity<>(datos,h),JsonNode.class);
    }
}
