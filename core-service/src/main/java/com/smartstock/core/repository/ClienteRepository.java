package com.smartstock.core.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.smartstock.core.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente,Long> {
    List<Cliente> findByActivoTrueOrderByNombreAscIdAsc();
}
