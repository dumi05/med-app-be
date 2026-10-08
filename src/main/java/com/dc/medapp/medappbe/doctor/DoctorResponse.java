package com.dc.medapp.medappbe.doctor;

public record DoctorResponse(Long id, String firstName, String lastName, String specialization) {
    public static DoctorResponse fromEntity(DoctorEntity doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getFirstName(),
                doctor.getLastName(),
                doctor.getSpecialization().getName()
        );
    }
}
