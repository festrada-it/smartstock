package com.smartstock.core.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.smartstock.core.model.ConsumoLote;

public interface ConsumoLoteRepository extends JpaRepository<ConsumoLote, Long> {
    List<ConsumoLote> findByDetalleVentaIdOrderByIdAsc(Long detalleId);
}
