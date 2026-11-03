package com.smartstock.core.dto;

import java.math.BigDecimal;
import com.smartstock.core.model.Categoria;

public record CategoriaResponse(Long id,String nombre,Integer diasAlertaVencimiento,BigDecimal umbralRotacion) {
    public static CategoriaResponse desde(Categoria c) {
        return new CategoriaResponse(c.getId(),c.getNombre(),c.getDiasAlertaVencimiento(),c.getUmbralRotacion());
    }
}
