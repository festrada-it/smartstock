package com.smartstock.core.service;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import com.smartstock.core.dto.*;
import com.smartstock.core.model.Cliente;
import com.smartstock.core.repository.*;
import com.smartstock.core.exception.*;

@Service @Transactional(readOnly=true)
public class ClienteService {
    private final ClienteRepository clientes;
    private final VentaRepository ventas;
    public ClienteService(ClienteRepository clientes,VentaRepository ventas) { this.clientes=clientes;this.ventas=ventas; }
    public List<ClienteResponse> listar(String q) {
        String texto=q==null?"":q.trim().toLowerCase(Locale.ROOT);
        return clientes.findByActivoTrueOrderByNombreAscIdAsc().stream()
                .filter(c->c.getNombre().toLowerCase(Locale.ROOT).contains(texto)).map(ClienteResponse::desde).toList();
    }
    public ClienteResponse obtener(Long id) { return ClienteResponse.desde(buscarActivo(id)); }
    @Transactional
    public ClienteResponse crear(ClienteRequest request) {
        Cliente cliente=new Cliente();aplicar(cliente,request);return ClienteResponse.desde(clientes.save(cliente));
    }
    @Transactional
    public ClienteResponse actualizar(Long id,ClienteRequest request) {
        Cliente cliente=buscarActivo(id);aplicar(cliente,request);return ClienteResponse.desde(cliente);
    }
    @Transactional
    public void eliminar(Long id) { buscarActivo(id).setActivo(false); }
    public HistorialClienteResponse pedidos(Long id,int page,int size) {
        if(page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE) {
            throw new NegocioException("La página debe ser válida y el tamaño debe estar entre 1 y 100");
        }
        buscarActivo(id);
        var historial=ventas.findHistorialCliente(id,PageRequest.of(page,size));
        return new HistorialClienteResponse(historial.getContent(),page,size,historial.getTotalElements(),historial.getTotalPages());
    }
    public List<ProductoClienteResponse> productos(Long id) { buscarActivo(id);return ventas.findProductosCliente(id); }
    private Cliente buscarActivo(Long id) {
        return clientes.findById(id).filter(Cliente::isActivo).orElseThrow(()->new RecursoNoEncontradoException("Cliente "+id+" no encontrado"));
    }
    private void aplicar(Cliente c,ClienteRequest r) {
        c.setNombre(r.nombre().trim());c.setDireccion(r.direccion()==null?null:r.direccion().trim());
        c.setTelefono(r.telefono()==null?null:r.telefono().trim());c.setCalidad(r.calidad());c.setClasificacion(r.clasificacion());
    }
}
