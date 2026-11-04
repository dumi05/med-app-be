package com.dc.medapp.medappbe.appointments.dto;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.appointments.CancellationReason;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.schedule.dto.SlotResponse;

import java.time.Instant;

public record AppointmentResponse (
        Long id,
        AppointmentStatus status,
        SlotResponse slot,
        Long patientId,
        String patientFirstName,
        String patientLastName,
        Boolean noShowRisk,
        Role bookedByRole,
        CancellationReason cancellationReason,
        String reassignedFromName,
        Instant reassignedAt
){
    public static AppointmentResponse fromEntity(AppointmentEntity entity, boolean includeNoShowRisk) {
        var patient = entity.getPatient();
        return new AppointmentResponse(
                entity.getId(),
                entity.getStatus(),
                SlotResponse.fromEntity(entity.getSlot()),
                patient.getId(),
                patient.getFirstName(),
                patient.getLastName(),
                includeNoShowRisk ? entity.isNoShowRisk() : null,
                entity.getBookedByRole() == null ? Role.PATIENT : entity.getBookedByRole(),
                entity.getCancellationReason() == null && entity.getStatus() == AppointmentStatus.CANCELLED ? CancellationReason.PATIENT : entity.getCancellationReason(),
                entity.getReassignedFrom() == null ? null : entity.getReassignedFrom().getFirstName() + " " + entity.getReassignedFrom().getLastName(),
                entity.getReassignedAt()
        );
    }
}
