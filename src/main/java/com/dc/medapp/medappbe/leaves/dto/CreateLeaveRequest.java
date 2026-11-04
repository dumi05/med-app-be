package com.dc.medapp.medappbe.leaves.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateLeaveRequest(
        Long doctorId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        Long replacementDoctorId
        ) {
}
