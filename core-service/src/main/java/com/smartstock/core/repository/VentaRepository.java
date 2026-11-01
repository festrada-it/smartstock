package com.smartstock.core.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import com.smartstock.core.model.Venta;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.smartstock.core.dto.PedidoClienteResponse;
import com.smartstock.core.dto.ProductoClienteResponse;

public interface VentaRepository extends JpaRepository<Venta, Long>, JpaSpecificationExecutor<Venta> {

    @Query(value = "SELECT nextval('numero_orden_seq')", nativeQuery = true)
    long siguienteNumeroOrden();

    @Query(value = "SELECT nextval('numero_factura_seq')", nativeQuery = true)
    long siguienteNumeroFactura();

    @Query("""
            SELECT new com.smartstock.core.dto.PedidoClienteResponse(v.id,v.fecha,v.total,v.anulada)
            FROM Venta v WHERE v.cliente.id=:clienteId ORDER BY v.fecha DESC,v.id DESC
            """)
    Page<PedidoClienteResponse> findHistorialCliente(@Param("clienteId") Long clienteId,Pageable pageable);

    @Query("""
            SELECT new com.smartstock.core.dto.ProductoClienteResponse(d.producto.id,d.producto.nombre,d.producto.marca,SUM(d.cantidad),MAX(v.fecha))
            FROM Venta v JOIN v.detalles d WHERE v.cliente.id=:clienteId AND v.anulada=false
              AND v.estado <> com.smartstock.core.model.EstadoPedido.PENDIENTE
            GROUP BY d.producto.id,d.producto.nombre,d.producto.marca ORDER BY SUM(d.cantidad) DESC,d.producto.id
            """)
    List<ProductoClienteResponse> findProductosCliente(@Param("clienteId") Long clienteId);

    @EntityGraph(attributePaths = {"cliente", "detalles", "detalles.producto"})
    @Query("SELECT DISTINCT v FROM Venta v WHERE v.id IN :ids")
    List<Venta> findConDetallesByIds(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Venta v WHERE v.id = :id")
    Optional<Venta> findByIdParaActualizar(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT v FROM Venta v
            JOIN FETCH v.usuario
            LEFT JOIN FETCH v.cliente
            LEFT JOIN FETCH v.detalles d
            LEFT JOIN FETCH d.producto
            WHERE v.fecha >= :desde AND v.anulada = false AND v.estado <> com.smartstock.core.model.EstadoPedido.PENDIENTE
            ORDER BY v.fecha DESC
            """)
    List<Venta> findDesdeConDetalles(@Param("desde") LocalDateTime desde);
}
