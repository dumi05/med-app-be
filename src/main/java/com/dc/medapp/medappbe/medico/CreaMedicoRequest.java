package com.dc.medapp.medappbe.medico;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreaMedicoRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String nome,
        @NotBlank String cognome,
        @NotNull Long specializzazioneId,
        boolean consensoDelega
) {
}
