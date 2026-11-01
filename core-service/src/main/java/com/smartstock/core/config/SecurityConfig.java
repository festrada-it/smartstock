package com.smartstock.core.config;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.smartstock.core.security.JwtAuthFilter;

/**
 * Reglas de acceso por rol (RBAC):
 * - ADMIN: todo (catálogo, usuarios, parámetros, ventas, compras).
 * - VENDEDOR: consulta de catálogo, carga de ventas e ingreso de mercadería.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String VENDEDOR = "VENDEDOR";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,ObjectMapper objectMapper) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                // Evita un segundo dispatch a /error que convertiría el 403 en un 401.
                .accessDeniedHandler((request, response, error) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(),
                            Map.of("error","No tenés permiso para realizar esta acción"));
                }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/health", "/actuator/health", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/productos/**", "/api/categorias/**", "/api/parametros/**").hasAnyRole(ADMIN, VENDEDOR)
                .requestMatchers("/api/productos/**", "/api/categorias/**", "/api/parametros/**").hasRole(ADMIN)
                .requestMatchers("/api/usuarios/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/proveedores/**").hasAnyRole(ADMIN, VENDEDOR)
                .requestMatchers("/api/proveedores/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/clientes/**").hasAnyRole(ADMIN, VENDEDOR)
                .requestMatchers("/api/clientes/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/ventas/*/anular", "/api/pedidos/*/cancelar").hasRole(ADMIN)
                .requestMatchers("/api/ventas/**", "/api/compras/**", "/api/pedidos/**").hasAnyRole(ADMIN, VENDEDOR)
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${smartstock.cors.origenes}") List<String> origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
