package com.smartstock.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.smartstock.core.controller.HealthController;

class HealthControllerTest {

    @Test
    void healthDevuelveEstadoOk() {
        Map<String, String> respuesta = new HealthController().health();

        assertEquals("OK", respuesta.get("estado"));
        assertEquals("core-service", respuesta.get("servicio"));
    }
}
