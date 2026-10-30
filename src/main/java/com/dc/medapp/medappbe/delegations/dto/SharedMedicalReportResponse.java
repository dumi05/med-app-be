package com.dc.medapp.medappbe.delegations.dto;

import com.dc.medapp.medappbe.reports.MedicalReportEntity;

import java.time.Instant;
import java.time.LocalDateTime;

public record SharedMedicalReportResponse(
        Long id,
        LocalDateTime visitDate,
        String doctorFirstName,
        String doctorLastName,
        String specialization,
        String diagnosisText,
        boolean attachmentAvailable,
        Instant completedAt
) {
    public static SharedMedicalReportResponse fromEntity(MedicalReportEntity entity, boolean attachmentAllowed){
        return new SharedMedicalReportResponse(
                entity.getId(),
                entity.getAppointment().getSlot().getStartTime(),
                entity.getAppointment().getSlot().getDoctor().getFirstName(),
                entity.getAppointment().getSlot().getDoctor().getLastName(),
                entity.getAppointment().getSlot().getDoctor().getSpecialization().getName(),
                entity.getDiagnosisText(),
                attachmentAllowed && entity.hasAttachment(),
                entity.getCompletedAt()
        );
    }
}
