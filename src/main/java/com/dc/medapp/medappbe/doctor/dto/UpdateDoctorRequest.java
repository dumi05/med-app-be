package com.dc.medapp.medappbe.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateDoctorRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull Long specializationId,
        boolean delegationConsent
) {
}
