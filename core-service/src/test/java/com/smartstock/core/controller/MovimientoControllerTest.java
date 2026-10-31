package com.smartstock.core.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.smartstock.core.config.SecurityConfig;
import com.smartstock.core.model.Rol;
import com.smartstock.core.model.Usuario;
import com.smartstock.core.security.JwtAuthFilter;
import com.smartstock.core.security.JwtService;
import com.smartstock.core.service.ProductoService;
import com.smartstock.core.exception.RecursoNoEncontradoException;

@WebMvcTest(ProductoController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
class MovimientoControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean ProductoService productos;

    @Test
    void ambosRolesPuedenConsultarHistorial() throws Exception {
        for (Rol rol : Rol.values()) {
            Usuario usuario = new Usuario();
            usuario.setEmail("persona@example.com");
            usuario.setNombre("Persona");
            usuario.setRol(rol);
            mvc.perform(get("/api/productos/1/movimientos")
                    .header("Authorization", "Bearer " + jwt.generar(usuario)))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
    }

    @Test
    void sinTokenNoPuedeConsultarHistorial() throws Exception {
        mvc.perform(get("/api/productos/1/movimientos")).andExpect(status().isUnauthorized());
    }

    @Test
    void productoAusenteDevuelve404() throws Exception {
        when(productos.movimientos(99L)).thenThrow(new RecursoNoEncontradoException("Producto 99 no encontrado"));
        Usuario usuario = new Usuario();
        usuario.setEmail("admin@example.com");
        usuario.setNombre("Admin");
        usuario.setRol(Rol.ADMIN);
        mvc.perform(get("/api/productos/99/movimientos")
                .header("Authorization", "Bearer " + jwt.generar(usuario)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Producto 99 no encontrado"));
    }
}
