package com.smartstock.core.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;

public record FechaEnvioRequest(@NotNull LocalDate fechaEnvio) {
}
