package com.smartstock.core.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.smartstock.core.dto.*;
import com.smartstock.core.model.EstadoPedido;
import com.smartstock.core.service.PedidoService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    private final PedidoService pedidos;

    public PedidoController(PedidoService pedidos) { this.pedidos = pedidos; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse crear(@Valid @RequestBody PedidoRequest request, Authentication autenticacion) {
        return pedidos.crear(request, autenticacion.getName());
    }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id) { return pedidos.obtener(id); }

    @PostMapping("/{id}/confirmar")
    public PedidoResponse confirmar(@PathVariable Long id, Authentication autenticacion) {
        return pedidos.confirmar(id, autenticacion.getName());
    }

    @PostMapping("/{id}/enviar")
    public PedidoResponse enviar(@PathVariable Long id) { return pedidos.enviar(id); }

    @PostMapping("/{id}/entregar")
    public PedidoResponse entregar(@PathVariable Long id) { return pedidos.entregar(id); }

    @PostMapping("/{id}/cancelar")
    public PedidoResponse cancelar(@PathVariable Long id, Authentication autenticacion) {
        return pedidos.cancelar(id, autenticacion.getName());
    }

    @PutMapping("/{id}/fecha-envio")
    public PedidoResponse cambiarFechaEnvio(@PathVariable Long id, @Valid @RequestBody FechaEnvioRequest request) {
        return pedidos.cambiarFechaEnvio(id, request.fechaEnvio());
    }

    @GetMapping
    public PaginaPedidosResponse listar(
            @RequestParam(required = false) EstadoPedido estado,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String numeroOrden,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return pedidos.listar(estado, clienteId, desde, hasta, numeroOrden, page, size);
    }
}
