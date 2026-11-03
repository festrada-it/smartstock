package com.smartstock.core.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(
        @NotBlank(message="El nombre del grupo es obligatorio") @Size(max=100,message="El grupo admite hasta 100 caracteres") String nombre,
        @Min(value=1,message="Los días de alerta deben ser mayores a cero") Integer diasAlertaVencimiento,
        @DecimalMin(value="0",message="El umbral debe estar entre 0 y 1") @DecimalMax(value="1",message="El umbral debe estar entre 0 y 1") BigDecimal umbralRotacion) {
    public CategoriaRequest {
        if(diasAlertaVencimiento==null) diasAlertaVencimiento=60;
        if(umbralRotacion==null) umbralRotacion=new BigDecimal("0.10");
    }
}
