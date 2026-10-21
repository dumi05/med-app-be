package com.dc.medapp.medappbe.patient.dto;

import com.dc.medapp.medappbe.appointments.dto.AppointmentResponse;
import com.dc.medapp.medappbe.reports.dto.MedicalReportResponse;

import java.util.List;

public record PatientDataExportResponse(
        PatientProfileResponse profile,
        List<AppointmentResponse> appointments,
        List<MedicalReportResponse> reports
) {
}
