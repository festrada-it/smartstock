package com.smartstock.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parametros")
public class Parametro {

    @Id
    private String clave;

    @Column(nullable = false)
    private String valor;

    private String descripcion;

    public String getClave() { return clave; }

    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }

    public String getDescripcion() { return descripcion; }
}
