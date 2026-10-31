package com.smartstock.core.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartstock.core.dto.ParametroRequest;
import com.smartstock.core.exception.RecursoNoEncontradoException;
import com.smartstock.core.model.Parametro;
import com.smartstock.core.repository.ParametroRepository;

import jakarta.validation.Valid;

/** Parámetros del módulo predictivo (días de reposición, cobertura, umbrales de criticidad). */
@RestController
@RequestMapping("/api/parametros")
public class ParametroController {

    private final ParametroRepository parametroRepository;

    public ParametroController(ParametroRepository parametroRepository) {
        this.parametroRepository = parametroRepository;
    }

    @GetMapping
    public List<Parametro> listar() {
        return parametroRepository.findAll();
    }

    @PutMapping("/{clave}")
    public Parametro actualizar(@PathVariable String clave, @Valid @RequestBody ParametroRequest request) {
        Parametro parametro = parametroRepository.findById(clave)
                .orElseThrow(() -> new RecursoNoEncontradoException("Parámetro " + clave + " no encontrado"));
        parametro.setValor(request.valor().trim());
        return parametroRepository.save(parametro);
    }
}
