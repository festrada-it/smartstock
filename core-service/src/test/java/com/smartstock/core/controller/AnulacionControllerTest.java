package com.smartstock.core.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.*;

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
import com.smartstock.core.service.VentaService;
import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.exception.RecursoNoEncontradoException;

@WebMvcTest(VentaController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
class AnulacionControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean VentaService ventas;

    @Test
    void adminPuedeAnular() throws Exception {
        mvc.perform(post("/api/ventas/1/anular").header("Authorization", token(Rol.ADMIN)))
                .andExpect(status().isOk());
        verify(ventas).anular(1L, "admin@example.com");
    }

    @Test
    void vendedorNoPuedeAnular() throws Exception {
        mvc.perform(post("/api/ventas/1/anular").header("Authorization", token(Rol.VENDEDOR)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ventas);
    }

    @Test
    void sinTokenNoPuedeAnular() throws Exception {
        mvc.perform(post("/api/ventas/1/anular")).andExpect(status().isUnauthorized());
    }

    @Test
    void traduceVentaAusenteYAnulacionRepetida() throws Exception {
        when(ventas.anular(99L, "admin@example.com")).thenThrow(new RecursoNoEncontradoException("Venta 99 no encontrada"));
        mvc.perform(post("/api/ventas/99/anular").header("Authorization", token(Rol.ADMIN)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Venta 99 no encontrada"));
        when(ventas.anular(1L, "admin@example.com")).thenThrow(new NegocioException("La venta 1 ya está anulada"));
        mvc.perform(post("/api/ventas/1/anular").header("Authorization", token(Rol.ADMIN)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("La venta 1 ya está anulada"));
    }

    private String token(Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setEmail("admin@example.com");
        usuario.setNombre("Persona");
        usuario.setRol(rol);
        return "Bearer " + jwt.generar(usuario);
    }
}
