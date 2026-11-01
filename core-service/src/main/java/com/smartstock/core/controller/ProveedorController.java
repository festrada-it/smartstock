package com.smartstock.core.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.smartstock.core.dto.*;
import com.smartstock.core.service.ProveedorService;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {
    private final ProveedorService servicio;
    public ProveedorController(ProveedorService servicio) { this.servicio=servicio; }
    @GetMapping
    public List<ProveedorResponse> listar(@RequestParam(required=false) String q) { return servicio.listar(q); }
    @GetMapping("/{id}")
    public ProveedorResponse obtener(@PathVariable Long id) { return servicio.obtener(id); }
    @GetMapping("/{id}/productos")
    public List<ProductoProveedorResponse> productos(@PathVariable Long id) { return servicio.productos(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ProveedorResponse crear(@Valid @RequestBody ProveedorRequest request) { return servicio.crear(request); }
    @PutMapping("/{id}")
    public ProveedorResponse actualizar(@PathVariable Long id,@Valid @RequestBody ProveedorRequest request) { return servicio.actualizar(id,request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) { servicio.eliminar(id); }
}
