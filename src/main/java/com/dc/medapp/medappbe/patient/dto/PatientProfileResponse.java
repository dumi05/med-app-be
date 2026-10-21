package com.dc.medapp.medappbe.patient.dto;

import com.dc.medapp.medappbe.patient.PatientEntity;

import java.time.Instant;
import java.time.LocalDate;

public record PatientProfileResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        LocalDate birthDate,
        boolean gdprConsent,
        String gdprConsentVersion,
        Instant gdprConsentTimestamp
) {
    public static PatientProfileResponse fromEntity(PatientEntity entity){
        return new PatientProfileResponse(
                entity.getId(),
                entity.getUser().getEmail(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getBirthDate(),
                entity.isGdprConsent(),
                entity.getGdprConsentVersion(),
                entity.getGdprConsentTimestamp()
        );
    }
}
