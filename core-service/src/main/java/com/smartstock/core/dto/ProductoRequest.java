package com.smartstock.core.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductoRequest(
        @NotBlank(message="El SKU es obligatorio") @Size(max=50,message="El SKU admite hasta 50 caracteres") String sku,
        @NotBlank(message="El nombre es obligatorio") @Size(max=150,message="El nombre admite hasta 150 caracteres") String nombre,
        Long categoriaId,
        @NotNull(message="El precio es obligatorio") @DecimalMin(value="0.0",message="El precio no puede ser negativo") BigDecimal precio,
        @NotNull(message="El costo es obligatorio") @DecimalMin(value="0.0",message="El costo no puede ser negativo") BigDecimal costo,
        @NotNull(message="El stock es obligatorio") @Min(value=0,message="El stock no puede ser negativo") Integer stockActual,
        @Min(value=0,message="El stock mínimo no puede ser negativo") Integer stockMinimo,
        @Size(max=100,message="La marca admite hasta 100 caracteres") String marca,
        Long proveedorId) {
    public ProductoRequest {
        if(stockMinimo==null) stockMinimo=30;
    }
}
