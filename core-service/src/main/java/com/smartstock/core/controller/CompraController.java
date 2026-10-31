package com.smartstock.core.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.smartstock.core.dto.CompraRequest;
import com.smartstock.core.dto.ProductoResponse;
import com.smartstock.core.service.CompraService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraService compraService;

    public CompraController(CompraService compraService) {
        this.compraService = compraService;
    }

    /** Ingreso de mercadería. Devuelve el producto con el stock actualizado. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse registrar(@Valid @RequestBody CompraRequest request, Authentication autenticacion) {
        return compraService.registrar(request, autenticacion.getName());
    }
}
