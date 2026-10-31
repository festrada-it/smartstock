package com.smartstock.core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartstock.core.model.Producto;

import jakarta.persistence.LockModeType;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByProveedorIdAndActivoTrueOrderByNombreAscIdAsc(Long proveedorId);

    @Query("SELECT p FROM Producto p LEFT JOIN FETCH p.categoria WHERE p.activo = true ORDER BY p.nombre")
    List<Producto> findActivosConCategoria();

    /**
     * Bloquea la fila del producto hasta que termine la transacción,
     * así dos ventas simultáneas no pueden descontar el mismo stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id = :id")
    Optional<Producto> findByIdParaActualizar(@Param("id") Long id);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);
}
