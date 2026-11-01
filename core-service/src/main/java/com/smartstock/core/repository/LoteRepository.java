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
    @Query("SELECT l FROM Lote l WHERE l.producto.id=:id ORDER BY l.id")
    List<Lote> bloquearPorProducto(@Param("id") Long id);
}
