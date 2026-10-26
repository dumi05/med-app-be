package com.dc.medapp.medappbe.doctor.dto;

import com.dc.medapp.medappbe.doctor.DoctorEntity;

public record DoctorResponse(Long id, String firstName, String lastName, Long specializationId, String specialization) {
    public static DoctorResponse fromEntity(DoctorEntity doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getFirstName(),
                doctor.getLastName(),
                doctor.getSpecialization().getId(),
                doctor.getSpecialization().getName()
        );
    }
}
