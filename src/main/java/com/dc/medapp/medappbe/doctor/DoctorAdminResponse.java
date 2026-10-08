package com.dc.medapp.medappbe.doctor;

public record DoctorAdminResponse(Long id,
                                  String firstName,
                                  String lastName,
                                  String email,
                                  String specialization,
                                  boolean delegationConsent) {
    public static DoctorAdminResponse fromEntity(DoctorEntity doctor) {
        return new DoctorAdminResponse(
                doctor.getId(),
                doctor.getFirstName(),
                doctor.getLastName(),
                doctor.getUser().getEmail(),
                doctor.getSpecialization().getName(),
                doctor.isDelegationConsent()
        );
    }
}
