package com.smartstock.core.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.smartstock.core.dto.*;
import com.smartstock.core.service.ClienteService;

@RestController @RequestMapping("/api/clientes")
public class ClienteController {
    private final ClienteService servicio;
    public ClienteController(ClienteService servicio) { this.servicio=servicio; }
    @GetMapping
    public List<ClienteResponse> listar(@RequestParam(required=false) String q) { return servicio.listar(q); }
    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Long id) { return servicio.obtener(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse crear(@Valid @RequestBody ClienteRequest request) { return servicio.crear(request); }
    @PutMapping("/{id}")
    public ClienteResponse actualizar(@PathVariable Long id,@Valid @RequestBody ClienteRequest request) { return servicio.actualizar(id,request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) { servicio.eliminar(id); }
    @GetMapping("/{id}/pedidos")
    public HistorialClienteResponse pedidos(@PathVariable Long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
        return servicio.pedidos(id,page,size);
    }
    @GetMapping("/{id}/productos")
    public List<ProductoClienteResponse> productos(@PathVariable Long id) { return servicio.productos(id); }
}
