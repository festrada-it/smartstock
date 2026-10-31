package com.smartstock.core.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.smartstock.core.dto.VentaRequest;
import com.smartstock.core.dto.VentaResponse;
import com.smartstock.core.service.VentaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse registrar(@Valid @RequestBody VentaRequest request, Authentication autenticacion) {
        return ventaService.registrar(request, autenticacion.getName());
    }

    @GetMapping("/hoy")
    public List<VentaResponse> ventasDelDia() {
        return ventaService.ventasDelDia();
    }

    @PostMapping("/{id}/anular")
    public VentaResponse anular(@PathVariable Long id, Authentication autenticacion) {
        return ventaService.anular(id, autenticacion.getName());
    }
}
