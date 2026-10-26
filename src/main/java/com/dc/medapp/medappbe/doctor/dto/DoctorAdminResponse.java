package com.dc.medapp.medappbe.doctor.dto;

import com.dc.medapp.medappbe.doctor.DoctorEntity;

public record DoctorAdminResponse(Long id,
                                  String firstName,
                                  String lastName,
                                  String email,
                                  Long specializationId,
                                  String specialization,
                                  boolean delegationConsent,
                                  boolean active) {
    public static DoctorAdminResponse fromEntity(DoctorEntity doctor) {
        return new DoctorAdminResponse(
                doctor.getId(),
                doctor.getFirstName(),
                doctor.getLastName(),
                doctor.getUser().getEmail(),
                doctor.getSpecialization().getId(),
                doctor.getSpecialization().getName(),
                doctor.isDelegationConsent(),
                doctor.getUser().isEnabled()
        );
    }
}
