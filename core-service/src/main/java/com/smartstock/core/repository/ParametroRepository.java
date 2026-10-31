package com.smartstock.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartstock.core.model.Parametro;

public interface ParametroRepository extends JpaRepository<Parametro, String> {
}
