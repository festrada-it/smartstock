package com.smartstock.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartstock.core.model.MovimientoStock;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {
    @Query("""
            SELECT m FROM MovimientoStock m
            LEFT JOIN FETCH m.usuario
            WHERE m.producto.id = :productoId
            ORDER BY m.fecha DESC, m.id DESC
            """)
    List<MovimientoStock> findHistorial(@Param("productoId") Long productoId);
}
