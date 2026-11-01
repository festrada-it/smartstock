package com.smartstock.core.support;

import com.smartstock.core.model.Rol;
import com.smartstock.core.model.Usuario;
import com.smartstock.core.security.JwtService;

/** Credenciales exclusivas de pruebas HTTP con el servicio JWT real. */
public final class JwtTestSupport {
    public static final String SECRET = "smartstock-secreto-de-pruebas-0123456789abcdef0123456789abcdef";
    public static final String SECRET_PROPERTY = "smartstock.jwt.secret=" + SECRET;

    private JwtTestSupport() {
    }

    public static String token(JwtService jwt, Rol rol, String email) {
        return "Bearer " + jwt.generar(usuario(rol, email));
    }

    public static Usuario usuario(Rol rol, String email) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNombre("Persona");
        usuario.setRol(rol);
        return usuario;
    }
}
