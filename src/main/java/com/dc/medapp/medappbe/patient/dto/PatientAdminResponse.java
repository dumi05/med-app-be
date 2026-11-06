package com.dc.medapp.medappbe.patient.dto;

import com.dc.medapp.medappbe.patient.PatientEntity;

import java.time.Instant;

public record PatientAdminResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Instant registeredAt,
        boolean mustChangePassword
) {
    public static PatientAdminResponse fromEntity(PatientEntity entity){
        return new PatientAdminResponse(
                entity.getId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getUser().getEmail(),
                entity.getUser().getCreatedAt(),
                entity.getUser().isMustChangePassword()
        );
    }
}
