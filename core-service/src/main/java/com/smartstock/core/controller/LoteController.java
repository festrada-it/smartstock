package com.smartstock.core.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.smartstock.core.dto.RetiroLoteRequest;
import com.smartstock.core.dto.RetiroLoteResponse;
import com.smartstock.core.service.LoteService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {
    private final LoteService lotes;

    public LoteController(LoteService lotes) { this.lotes = lotes; }

    /** Registra que un lote vencido salió del local. El retiro físico se coordina fuera del sistema. */
    @PostMapping("/{id}/retirar")
    public RetiroLoteResponse retirar(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) RetiroLoteRequest request,
                                      Authentication autenticacion) {
        return lotes.retirarVencido(id, request == null ? null : request.observacion(), autenticacion.getName());
    }
}
