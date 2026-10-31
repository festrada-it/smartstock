package com.smartstock.core.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import com.smartstock.core.model.Venta;
import jakarta.persistence.LockModeType;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Venta v WHERE v.id = :id")
    Optional<Venta> findByIdParaActualizar(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT v FROM Venta v
            JOIN FETCH v.usuario
            LEFT JOIN FETCH v.detalles d
            LEFT JOIN FETCH d.producto
            WHERE v.fecha >= :desde AND v.anulada = false
            ORDER BY v.fecha DESC
            """)
    List<Venta> findDesdeConDetalles(@Param("desde") LocalDateTime desde);
}
