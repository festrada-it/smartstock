package com.smartstock.core.dto;

import com.smartstock.core.model.Calidad;
import jakarta.validation.constraints.*;

public record ClienteRequest(
        @NotBlank(message="El nombre es obligatorio") @Size(max=150,message="El nombre admite hasta 150 caracteres") String nombre,
        @Size(max=255,message="La dirección admite hasta 255 caracteres") String direccion,
        @Size(max=50,message="El teléfono admite hasta 50 caracteres") String telefono,
        @NotNull(message="La calidad es obligatoria") Calidad calidad,
        @NotNull(message="La clasificación es obligatoria") @Min(value=1,message="La clasificación debe estar entre 1 y 5") @Max(value=5,message="La clasificación debe estar entre 1 y 5") Integer clasificacion) {
}
