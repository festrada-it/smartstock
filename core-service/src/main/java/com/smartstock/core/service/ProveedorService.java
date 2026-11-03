package com.smartstock.core.service;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartstock.core.dto.*;
import com.smartstock.core.model.Proveedor;
import com.smartstock.core.repository.*;
import com.smartstock.core.exception.RecursoNoEncontradoException;

@Service
@Transactional(readOnly = true)
public class ProveedorService {
    private final ProveedorRepository proveedores;
    private final ProductoRepository productos;
    public ProveedorService(ProveedorRepository proveedores,ProductoRepository productos) {
        this.proveedores=proveedores; this.productos=productos;
    }
    public List<ProveedorResponse> listar(String q) {
        String texto=q==null?"":q.trim().toLowerCase(Locale.ROOT);
        return proveedores.findByActivoTrueOrderByNombreAscIdAsc().stream()
                .filter(p->p.getNombre().toLowerCase(Locale.ROOT).contains(texto)).map(ProveedorResponse::desde).toList();
    }
    public ProveedorResponse obtener(Long id) { return ProveedorResponse.desde(buscarActivo(id)); }
    public List<ProductoProveedorResponse> productos(Long id) {
        buscarActivo(id);
        return productos.findByProveedorIdAndActivoTrueOrderByNombreAscIdAsc(id).stream().map(ProductoProveedorResponse::desde).toList();
    }
    @Transactional
    public ProveedorResponse crear(ProveedorRequest request) {
        Proveedor proveedor=new Proveedor(); aplicar(proveedor,request);
        return ProveedorResponse.desde(proveedores.save(proveedor));
    }
    @Transactional
    public ProveedorResponse actualizar(Long id,ProveedorRequest request) {
        Proveedor proveedor=buscarActivo(id); aplicar(proveedor,request);
        return ProveedorResponse.desde(proveedor);
    }
    @Transactional
    public void eliminar(Long id) { buscarActivo(id).setActivo(false); }
    private Proveedor buscarActivo(Long id) {
        return proveedores.findById(id).filter(Proveedor::isActivo)
                .orElseThrow(()->new RecursoNoEncontradoException("Proveedor "+id+" no encontrado"));
    }
    private void aplicar(Proveedor p,ProveedorRequest r) {
        p.setNombre(r.nombre().trim()); p.setDireccion(r.direccion()==null?null:r.direccion().trim());
        p.setTelefono(r.telefono()==null?null:r.telefono().trim()); p.setLeadTimeDias(r.leadTimeDias());
        p.setCalidad(r.calidad()); p.setClasificacion(r.clasificacion());
    }
}
