package com.dc.medapp.medappbe.patient.dto;

import com.dc.medapp.medappbe.patient.PatientEntity;

public record PatientSearchResponse(
        Long id,
        String firstName,
        String lastName,
        String email
) {
    public static PatientSearchResponse fromEntity(PatientEntity entity){
        return new PatientSearchResponse(
                entity.getId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getUser().getEmail()
        );
    }
}
