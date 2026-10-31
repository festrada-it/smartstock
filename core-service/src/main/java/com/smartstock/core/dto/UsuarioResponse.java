package com.smartstock.core.dto;

import com.smartstock.core.model.Rol;
import com.smartstock.core.model.Usuario;

public record UsuarioResponse(Long id, String nombre, String email, Rol rol, Boolean activo) {

    public static UsuarioResponse desde(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRol(), u.getActivo());
    }
}
