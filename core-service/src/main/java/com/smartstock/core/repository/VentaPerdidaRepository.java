package com.smartstock.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartstock.core.model.VentaPerdida;

public interface VentaPerdidaRepository extends JpaRepository<VentaPerdida, Long> {
}
