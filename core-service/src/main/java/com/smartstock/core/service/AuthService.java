package com.smartstock.core.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.smartstock.core.dto.LoginRequest;
import com.smartstock.core.dto.LoginResponse;
import com.smartstock.core.exception.CredencialesInvalidasException;
import com.smartstock.core.model.Usuario;
import com.smartstock.core.repository.UsuarioRepository;
import com.smartstock.core.security.JwtService;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email().trim().toLowerCase())
                .filter(Usuario::getActivo)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(CredencialesInvalidasException::new);

        return new LoginResponse(jwtService.generar(usuario), usuario.getNombre(), usuario.getEmail(), usuario.getRol());
    }
}
