package com.smartstock.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartstock.core.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByActivoTrueOrderByNombre();
}
