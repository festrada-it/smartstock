package com.smartstock.core.dto;

import com.smartstock.core.model.Rol;

public record LoginResponse(String token, String nombre, String email, Rol rol) {
}
