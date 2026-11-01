package com.smartstock.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smartstock.core.dto.VentaRequest;
import com.smartstock.core.dto.VentaResponse;
import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.model.Producto;
import com.smartstock.core.model.Rol;
import com.smartstock.core.model.Usuario;
import com.smartstock.core.model.Venta;
import com.smartstock.core.repository.MovimientoStockRepository;
import com.smartstock.core.repository.ProductoRepository;
import com.smartstock.core.repository.UsuarioRepository;
import com.smartstock.core.repository.VentaRepository;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    private static final String EMAIL = "vendedor@smartstock.com";

    @Mock private VentaRepository ventaRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private MovimientoStockRepository movimientoRepository;

    @Mock private com.smartstock.core.repository.ClienteRepository clienteRepository;
    @Mock private org.springframework.jdbc.core.JdbcTemplate db;
    @InjectMocks private VentaService ventaService;

    private Producto yerba;

    @BeforeEach
    void setUp() {
        Usuario vendedor = new Usuario();
        vendedor.setNombre("Vendedor");
        vendedor.setEmail(EMAIL);
        vendedor.setRol(Rol.VENDEDOR);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(vendedor));

        var cliente = new com.smartstock.core.model.Cliente();
        cliente.setNombre("Consumidor final");
        when(clienteRepository.findFirstByNombreAndActivoTrueOrderByIdAsc("Consumidor final"))
                .thenReturn(Optional.of(cliente));
        yerba = new Producto();
        yerba.setId(1L);
        yerba.setNombre("Yerba mate 1kg");
        yerba.setPrecio(new BigDecimal("4500.00"));
        yerba.setStockActual(10);
        when(productoRepository.findByIdParaActualizar(1L)).thenReturn(Optional.of(yerba));
    }

    @Test
    void registrarVentaDescuentaStockYCalculaTotal() {
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));

        VentaResponse respuesta = ventaService.registrar(
                new VentaRequest(List.of(new VentaRequest.Item(1L, 3))), EMAIL);

        assertEquals(7, yerba.getStockActual());
        assertEquals(new BigDecimal("13500.00"), respuesta.total());
        assertEquals(1, respuesta.items().size());
        verify(movimientoRepository).saveAll(anyList());
    }

    @Test
    void registrarVentaSinStockSuficienteFallaYNoGuarda() {
        VentaRequest request = new VentaRequest(List.of(new VentaRequest.Item(1L, 11)));

        NegocioException error = assertThrows(NegocioException.class, () -> ventaService.registrar(request, EMAIL));

        assertEquals(true, error.getMessage().contains("Stock insuficiente"));
        verify(ventaRepository, never()).save(any());
    }
}
