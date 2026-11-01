package com.smartstock.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartstock.core.model.MovimientoStock;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {
    @Query(value = """
            SELECT m FROM MovimientoStock m
            LEFT JOIN FETCH m.usuario
            WHERE m.producto.id = :productoId
            ORDER BY m.fecha DESC, m.id DESC
            """, countQuery = "SELECT COUNT(m) FROM MovimientoStock m WHERE m.producto.id = :productoId")
    Page<MovimientoStock> findHistorial(@Param("productoId") Long productoId, Pageable pageable);
}
