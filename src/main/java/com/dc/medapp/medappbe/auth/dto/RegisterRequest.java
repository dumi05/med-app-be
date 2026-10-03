package com.dc.medapp.medappbe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(@NotBlank @Email String email,
                              @NotBlank @Size(min = 8) String password,
                              @NotBlank String nome,
                              @NotBlank String cognome,
                              @NotNull LocalDate dataNascita,
                              boolean consensoGdpr,
                              @NotBlank String consensoGdprVersion) {
}
