package com.smartstock.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.smartstock.core.model.Rol;
import com.smartstock.core.model.Usuario;

import io.jsonwebtoken.Claims;

class JwtServiceTest {

    private static final String SECRETO = "secreto-de-test-con-al-menos-treinta-y-dos-bytes-de-largo";

    private final JwtService jwtService = new JwtService(SECRETO, 60);

    @Test
    void tokenGeneradoEsValidoYContieneElRol() {
        Usuario admin = new Usuario();
        admin.setNombre("Admin");
        admin.setEmail("admin@smartstock.com");
        admin.setRol(Rol.ADMIN);

        Claims claims = jwtService.validar(jwtService.generar(admin)).orElseThrow();

        assertEquals("admin@smartstock.com", claims.getSubject());
        assertEquals("ADMIN", claims.get("rol", String.class));
    }

    @Test
    void tokenFirmadoConOtroSecretoEsRechazado() {
        Usuario usuario = new Usuario();
        usuario.setNombre("X");
        usuario.setEmail("x@x.com");
        usuario.setRol(Rol.VENDEDOR);
        String tokenAjeno = new JwtService("otro-secreto-distinto-con-mas-de-treinta-y-dos-bytes!!", 60).generar(usuario);

        assertTrue(jwtService.validar(tokenAjeno).isEmpty());
        assertTrue(jwtService.validar("basura").isEmpty());
    }
}
