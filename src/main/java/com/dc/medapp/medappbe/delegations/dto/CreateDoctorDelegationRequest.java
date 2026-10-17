package com.dc.medapp.medappbe.delegations.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateDoctorDelegationRequest(
        @NotNull Long delegateDoctorId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
        ) {
}
