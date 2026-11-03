package com.smartstock.core.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categorias")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(name="dias_alerta_vencimiento",nullable=false)
    private Integer diasAlertaVencimiento=60;

    @Column(name="umbral_rotacion",nullable=false)
    private BigDecimal umbralRotacion=new BigDecimal("0.10");

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Integer getDiasAlertaVencimiento() { return diasAlertaVencimiento; }
    public void setDiasAlertaVencimiento(Integer dias) { this.diasAlertaVencimiento=dias; }
    public BigDecimal getUmbralRotacion() { return umbralRotacion; }
    public void setUmbralRotacion(BigDecimal umbral) { this.umbralRotacion=umbral; }
}
