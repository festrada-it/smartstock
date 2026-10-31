package com.smartstock.core.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.smartstock.core.config.SecurityConfig;
import com.smartstock.core.dto.LoginResponse;
import com.smartstock.core.exception.CredencialesInvalidasException;
import com.smartstock.core.exception.NegocioException;
import com.smartstock.core.exception.RecursoNoEncontradoException;
import com.smartstock.core.model.Rol;
import com.smartstock.core.model.Usuario;
import com.smartstock.core.repository.CategoriaRepository;
import com.smartstock.core.repository.ParametroRepository;
import com.smartstock.core.security.JwtAuthFilter;
import com.smartstock.core.security.JwtService;
import com.smartstock.core.service.*;

/** Pruebas HTTP con la cadena de seguridad y JWT reales; solo se simula la capa de negocio. */
@WebMvcTest(properties = "smartstock.jwt.secret=smartstock-secreto-de-pruebas-0123456789abcdef0123456789abcdef")
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
class ControllersTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean AuthService auth;
    @MockBean ProductoService productos;
    @MockBean VentaService ventas;
    @MockBean CompraService compras;
    @MockBean UsuarioService usuarios;
    @MockBean ProveedorService proveedores;
    @MockBean ClienteService clientes;
    @MockBean CategoriaRepository categorias;
    @MockBean ParametroRepository parametros;

    static Stream<Arguments> rutas() {
        String producto = """
                {"sku":"A1","nombre":"Yerba","precio":10,"costo":5,"stockActual":10,"stockMinimo":1}
                """;
        return Stream.of(
                Arguments.of("GET", "/api/clientes", "", 200, true),
                Arguments.of("GET", "/api/clientes/1", "", 200, true),
                Arguments.of("GET", "/api/clientes/1/pedidos", "", 200, true),
                Arguments.of("GET", "/api/clientes/1/productos", "", 200, true),
                Arguments.of("POST", "/api/clientes", "{\"nombre\":\"Cliente\",\"calidad\":\"BUENA\",\"clasificacion\":4}", 201, false),
                Arguments.of("PUT", "/api/clientes/1", "{\"nombre\":\"Cliente\",\"calidad\":\"BUENA\",\"clasificacion\":4}", 200, false),
                Arguments.of("DELETE", "/api/clientes/1", "", 204, false),
                Arguments.of("GET", "/api/proveedores", "", 200, true),
                Arguments.of("GET", "/api/proveedores/1", "", 200, true),
                Arguments.of("GET", "/api/proveedores/1/productos", "", 200, true),
                Arguments.of("POST", "/api/proveedores", "{\"nombre\":\"Proveedor\",\"leadTimeDias\":3,\"calidad\":\"BUENA\",\"clasificacion\":4}", 201, false),
                Arguments.of("PUT", "/api/proveedores/1", "{\"nombre\":\"Proveedor\",\"leadTimeDias\":3,\"calidad\":\"BUENA\",\"clasificacion\":4}", 200, false),
                Arguments.of("DELETE", "/api/proveedores/1", "", 204, false),
                Arguments.of("GET", "/api/productos", "", 200, true),
                Arguments.of("GET", "/api/productos/1", "", 200, true),
                Arguments.of("POST", "/api/productos", producto, 201, false),
                Arguments.of("PUT", "/api/productos/1", producto, 200, false),
                Arguments.of("DELETE", "/api/productos/1", "", 204, false),
                Arguments.of("GET", "/api/categorias", "", 200, true),
                Arguments.of("PUT", "/api/categorias/1", "{\"nombre\":\"Almacén\",\"diasAlertaVencimiento\":60,\"umbralRotacion\":0.1}", 200, false),
                Arguments.of("GET", "/api/parametros", "", 200, true),
                Arguments.of("GET", "/api/usuarios", "", 200, false),
                Arguments.of("POST", "/api/usuarios", "{\"nombre\":\"Ana\",\"email\":\"ana@example.com\",\"password\":\"prueba123\",\"rol\":\"VENDEDOR\"}", 201, false),
                Arguments.of("PUT", "/api/usuarios/2/estado?activo=false", "", 200, false),
                Arguments.of("POST", "/api/ventas", "{\"items\":[{\"productoId\":1,\"cantidad\":2}]}", 201, true),
                Arguments.of("GET", "/api/ventas/hoy", "", 200, true),
                Arguments.of("POST", "/api/compras", "{\"productoId\":1,\"cantidad\":2}", 201, true));
    }

    @ParameterizedTest
    @MethodSource("rutas")
    void sinTokenRechazaRutasProtegidas(String metodo, String ruta, String cuerpo, int estado, boolean vendedor) throws Exception {
        mvc.perform(peticion(metodo, ruta, cuerpo)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("rutas")
    void adminAccedeATodasLasRutas(String metodo, String ruta, String cuerpo, int estado, boolean vendedor) throws Exception {
        if(ruta.equals("/api/categorias/1")) {
            var categoria=new com.smartstock.core.model.Categoria();categoria.setId(1L);categoria.setNombre("Almacén");
            when(categorias.findById(1L)).thenReturn(java.util.Optional.of(categoria));
            when(categorias.save(any())).thenAnswer(inv->inv.getArgument(0));
        }
        mvc.perform(peticion(metodo, ruta, cuerpo).header("Authorization", token(Rol.ADMIN)))
                .andExpect(status().is(estado));
    }

    @ParameterizedTest
    @MethodSource("rutas")
    void vendedorRespetaPermisos(String metodo, String ruta, String cuerpo, int estado, boolean vendedor) throws Exception {
        mvc.perform(peticion(metodo, ruta, cuerpo).header("Authorization", token(Rol.VENDEDOR)))
                .andExpect(status().is(vendedor ? estado : 403));
    }

    @Test
    void adminCreaCategoria() throws Exception {
        var categoria = new com.smartstock.core.model.Categoria();
        categoria.setNombre("Alimentos");
        when(categorias.save(any())).thenReturn(categoria);
        mvc.perform(post("/api/categorias").header("Authorization", token(Rol.ADMIN))
                .contentType("application/json").content("{\"nombre\":\"Alimentos\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value("Alimentos"));
    }

    @Test
    void adminActualizaParametroExistente() throws Exception {
        var parametro = new com.smartstock.core.model.Parametro();
        parametro.setValor("3");
        when(parametros.findById("dias_cobertura")).thenReturn(java.util.Optional.of(parametro));
        when(parametros.save(any())).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(put("/api/parametros/dias_cobertura").header("Authorization", token(Rol.ADMIN))
                .contentType("application/json").content("{\"valor\":\" 7 \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valor").value("7"));
        verify(parametros).save(parametro);
    }

    @Test
    void parametroInexistenteDevuelve404() throws Exception {
        mvc.perform(put("/api/parametros/inexistente").header("Authorization", token(Rol.ADMIN))
                .contentType("application/json").content("{\"valor\":\"7\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void vendedorNoPuedeCrearCategoriaNiModificarParametro() throws Exception {
        mvc.perform(post("/api/categorias").header("Authorization", token(Rol.VENDEDOR))
                .contentType("application/json").content("{\"nombre\":\"Alimentos\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/parametros/dias_cobertura").header("Authorization", token(Rol.VENDEDOR))
                .contentType("application/json").content("{\"valor\":\"7\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void clasificacionDeClienteDebeSerEntera() throws Exception {
        mvc.perform(post("/api/clientes").header("Authorization",token(Rol.ADMIN))
                .contentType("application/json").content("{\"nombre\":\"Cliente\",\"calidad\":\"BUENA\",\"clasificacion\":2.5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        mvc.perform(post("/api/ventas").header("Authorization",token(Rol.VENDEDOR))
                .contentType("application/json").content("{\"clienteId\":1.9,\"items\":[{\"productoId\":1,\"cantidad\":1}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void healthYLoginSonPublicos() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("OK"));
        when(auth.login(any())).thenReturn(new LoginResponse("jwt", "Ana", "ana@example.com", Rol.ADMIN));
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"ana@example.com\",\"password\":\"prueba123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").value("jwt"));
    }

    @Test
    void credencialesIncorrectasDevuelven401() throws Exception {
        when(auth.login(any())).thenThrow(new CredencialesInvalidasException());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"ana@example.com\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void rechazaTokenInvalidoYVencido() throws Exception {
        mvc.perform(get("/api/productos").header("Authorization", "Bearer invalido"))
                .andExpect(status().isUnauthorized());
        Usuario usuario = usuario(Rol.ADMIN);
        String vencido = new JwtService("smartstock-secreto-de-pruebas-0123456789abcdef0123456789abcdef", -1).generar(usuario);
        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + vencido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validaVentasYComprasAntesDeInvocarNegocio() throws Exception {
        mvc.perform(post("/api/ventas").header("Authorization", token(Rol.VENDEDOR))
                .contentType("application/json").content("{\"items\":[{\"productoId\":1,\"cantidad\":0}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        mvc.perform(post("/api/compras").header("Authorization", token(Rol.ADMIN))
                .contentType("application/json").content("{\"productoId\":1,\"cantidad\":-1}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(ventas, compras);
    }

    @Test
    void propagaIdentidadDelJwtAlRegistrarVenta() throws Exception {
        mvc.perform(post("/api/ventas").header("Authorization", token(Rol.VENDEDOR))
                .contentType("application/json").content("{\"items\":[{\"productoId\":1,\"cantidad\":1}]}"))
                .andExpect(status().isCreated());
        verify(ventas).registrar(any(), eq("persona@example.com"));
    }

    @Test
    void traduceErroresDeNegocioYRecursoAusente() throws Exception {
        when(productos.obtener(anyLong())).thenThrow(new RecursoNoEncontradoException("Producto no encontrado"));
        mvc.perform(get("/api/productos/999").header("Authorization", token(Rol.ADMIN)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Producto no encontrado"));
        when(ventas.registrar(any(), any())).thenThrow(new NegocioException("Stock insuficiente"));
        mvc.perform(post("/api/ventas").header("Authorization", token(Rol.VENDEDOR))
                .contentType("application/json").content("{\"items\":[{\"productoId\":1,\"cantidad\":1}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Stock insuficiente"));
    }

    private MockHttpServletRequestBuilder peticion(String metodo, String ruta, String cuerpo) {
        return request(HttpMethod.valueOf(metodo), ruta).contentType("application/json").content(cuerpo);
    }

    private String token(Rol rol) {
        return "Bearer " + jwt.generar(usuario(rol));
    }

    private Usuario usuario(Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setEmail("persona@example.com");
        usuario.setNombre("Persona");
        usuario.setRol(rol);
        return usuario;
    }
}
