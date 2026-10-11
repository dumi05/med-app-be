package com.dc.medapp.medappbe.security;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileResolver {
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    public Long patientIdOf(UserEntity user){
        return patientRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Paziente non trovato."))
                .getId();
    }

    public Long doctorIdOf(UserEntity user){
        return doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Dottore non trovato."))
                .getId();
    }

}
