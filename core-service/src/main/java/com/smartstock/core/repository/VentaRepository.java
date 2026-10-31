package com.smartstock.core.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartstock.core.model.Venta;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    @Query("""
            SELECT DISTINCT v FROM Venta v
            JOIN FETCH v.usuario
            LEFT JOIN FETCH v.detalles d
            LEFT JOIN FETCH d.producto
            WHERE v.fecha >= :desde
            ORDER BY v.fecha DESC
            """)
    List<Venta> findDesdeConDetalles(@Param("desde") LocalDateTime desde);
}
