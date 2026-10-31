package com.smartstock.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartstock.core.model.MovimientoStock;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {
}
