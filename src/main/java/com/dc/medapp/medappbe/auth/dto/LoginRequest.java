package com.dc.medapp.medappbe.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank String email, @NotBlank @Size(max = 256) String password) {
}
