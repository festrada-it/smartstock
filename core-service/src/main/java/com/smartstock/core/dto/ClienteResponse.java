package com.smartstock.core.dto;

import com.smartstock.core.model.Calidad;
import com.smartstock.core.model.Cliente;

public record ClienteResponse(Long id,String nombre,String direccion,String telefono,Calidad calidad,Integer clasificacion,boolean activo) {
    public static ClienteResponse desde(Cliente c) {
        return new ClienteResponse(c.getId(),c.getNombre(),c.getDireccion(),c.getTelefono(),c.getCalidad(),c.getClasificacion(),c.isActivo());
    }
}
