package com.smartstock.core.model;

import jakarta.persistence.*;

@Entity
@Table(name = "proveedores")
public class Proveedor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String nombre;
    private String direccion;
    @Column(length = 50)
    private String telefono;
    @Column(name = "lead_time_dias", nullable = false)
    private Integer leadTimeDias;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Calidad calidad;
    @Column(nullable = false)
    private Integer clasificacion;
    @Column(nullable = false)
    private boolean activo = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id=id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre=nombre; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion=direccion; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono=telefono; }
    public Integer getLeadTimeDias() { return leadTimeDias; }
    public void setLeadTimeDias(Integer dias) { this.leadTimeDias=dias; }
    public Calidad getCalidad() { return calidad; }
    public void setCalidad(Calidad calidad) { this.calidad=calidad; }
    public Integer getClasificacion() { return clasificacion; }
    public void setClasificacion(Integer clasificacion) { this.clasificacion=clasificacion; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo=activo; }
}
