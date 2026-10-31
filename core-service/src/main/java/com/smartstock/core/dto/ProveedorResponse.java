package com.smartstock.core.dto;

import com.smartstock.core.model.Calidad;
import com.smartstock.core.model.Proveedor;

public record ProveedorResponse(Long id,String nombre,String direccion,String telefono,
        Integer leadTimeDias,Calidad calidad,Integer clasificacion,boolean activo) {
    public static ProveedorResponse desde(Proveedor p) {
        return new ProveedorResponse(p.getId(),p.getNombre(),p.getDireccion(),p.getTelefono(),
                p.getLeadTimeDias(),p.getCalidad(),p.getClasificacion(),p.isActivo());
    }
}
