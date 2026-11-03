package com.smartstock.core.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.smartstock.core.model.Proveedor;

public interface ProveedorRepository extends JpaRepository<Proveedor,Long> {
    List<Proveedor> findByActivoTrueOrderByNombreAscIdAsc();
}
