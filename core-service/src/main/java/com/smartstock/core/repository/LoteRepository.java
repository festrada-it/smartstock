package com.smartstock.core.repository;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.smartstock.core.model.Lote;
import jakarta.persistence.LockModeType;

public interface LoteRepository extends JpaRepository<Lote, Long> {
    @Query("SELECT l FROM Lote l LEFT JOIN FETCH l.proveedor WHERE l.producto.id=:id AND (:todos=true OR l.cantidadDisponible>0) ORDER BY l.fechaVencimiento ASC NULLS LAST,l.id")
    List<Lote> consultar(@Param("id") Long id, @Param("todos") boolean todos);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lote l WHERE l.producto.id=:id AND l.cantidadDisponible>0 ORDER BY l.fechaVencimiento ASC NULLS LAST,l.id")
    List<Lote> bloquearDisponiblesFefo(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lote l WHERE l.producto.id=:id ORDER BY l.id")
    List<Lote> bloquearPorProducto(@Param("id") Long id);

    /** Solo el id del producto, sin bloquear: permite tomar el bloqueo del producto antes que el del lote. */
    @Query("SELECT l.producto.id FROM Lote l WHERE l.id=:id")
    java.util.Optional<Long> productoDe(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lote l WHERE l.id=:id")
    java.util.Optional<Lote> bloquear(@Param("id") Long id);
}
