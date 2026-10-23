package com.dc.medapp.medappbe.delegations.dto;

import com.dc.medapp.medappbe.reports.MedicalReportEntity;

import java.time.Instant;
import java.time.LocalDateTime;

public record SharedMedicalReportResponse(
        LocalDateTime visitDate,
        String doctorFirstName,
        String doctorLastName,
        String specialization,
        String diagnosisText,
        String attachmentUrl,
        Instant completedAt
) {
    public static SharedMedicalReportResponse fromEntity(MedicalReportEntity entity){
        return new SharedMedicalReportResponse(
                entity.getAppointment().getSlot().getStartTime(),
                entity.getAppointment().getSlot().getDoctor().getFirstName(),
                entity.getAppointment().getSlot().getDoctor().getLastName(),
                entity.getAppointment().getSlot().getDoctor().getSpecialization().getName(),
                entity.getDiagnosisText(),
                entity.getAttachmentUrl(),
                entity.getCompletedAt()
        );
    }
}
