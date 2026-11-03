package com.smartstock.core.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.smartstock.core.dto.CategoriaRequest;
import com.smartstock.core.dto.CategoriaResponse;
import com.smartstock.core.exception.RecursoNoEncontradoException;
import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.model.Categoria;
import com.smartstock.core.repository.CategoriaRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;

    public CategoriaController(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @GetMapping
    public List<CategoriaResponse> listar() {
        return categoriaRepository.findAllByOrderByNombre().stream().map(CategoriaResponse::desde).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse crear(@Valid @RequestBody CategoriaRequest request) {
        String nombre = request.nombre().trim();
        if (categoriaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new NegocioException("Ya existe la categoría " + nombre);
        }
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setDiasAlertaVencimiento(request.diasAlertaVencimiento());
        categoria.setUmbralRotacion(request.umbralRotacion());
        return CategoriaResponse.desde(categoriaRepository.save(categoria));
    }

    @PutMapping("/{id}")
    public CategoriaResponse actualizar(@PathVariable Long id,@Valid @RequestBody CategoriaRequest request) {
        Categoria categoria=categoriaRepository.findById(id)
                .orElseThrow(()->new RecursoNoEncontradoException("Grupo "+id+" no encontrado"));
        if(categoriaRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre().trim(),id)) {
            throw new NegocioException("Ya existe el grupo "+request.nombre().trim());
        }
        categoria.setNombre(request.nombre().trim());
        categoria.setDiasAlertaVencimiento(request.diasAlertaVencimiento());
        categoria.setUmbralRotacion(request.umbralRotacion());
        return CategoriaResponse.desde(categoriaRepository.save(categoria));
    }
}
