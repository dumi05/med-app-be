package com.dc.medapp.medappbe.doctor.dto;

import com.dc.medapp.medappbe.common.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDoctorRequest(
        @NotBlank @ValidEmail String email,
        @NotBlank @Size(max = 256) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull Long specializationId,
        boolean delegationConsent
) {
}
