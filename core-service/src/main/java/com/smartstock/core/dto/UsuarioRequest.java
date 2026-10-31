package com.smartstock.core.dto;

import com.smartstock.core.model.Rol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 72) String password,
        @NotNull Rol rol) {
}
