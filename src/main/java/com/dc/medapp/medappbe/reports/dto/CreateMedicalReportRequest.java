package com.dc.medapp.medappbe.reports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMedicalReportRequest(
        @NotNull Long appointmentId,
        @NotBlank @Size(max = 5000) String diagnosisText
){
}
