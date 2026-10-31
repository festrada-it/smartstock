package com.smartstock.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParametroRequest(@NotBlank @Size(max = 100) String valor) {
}
