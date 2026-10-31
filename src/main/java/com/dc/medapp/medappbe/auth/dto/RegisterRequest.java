package com.dc.medapp.medappbe.auth.dto;

import com.dc.medapp.medappbe.common.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(@NotBlank @ValidEmail String email,
                              @NotBlank @Size(max = 256) String password,
                              @NotBlank String firstName,
                              @NotBlank String lastName,
                              @NotNull LocalDate birthDate,
                              boolean gdprConsent,
                              @NotBlank String gdprConsentVersion) {
}
