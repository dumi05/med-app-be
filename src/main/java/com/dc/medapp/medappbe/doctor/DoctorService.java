package com.dc.medapp.medappbe.doctor;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.spceialization.SpecializationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorService {
    private final DoctorRepository doctorRepository;
    private final SpecializationRepository specializationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public DoctorEntity createDoctor(String email, String password, String firstName, String lastName, Long specializationId, boolean delegationConsent) {
        var specialization = specializationRepository.findById(specializationId)
                .orElseThrow(() -> new ResourceNotFoundException("Specializzazione non trovata: " + specializationId));

        if(userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email già in uso: " + email);
        }
        var user = userRepository.save(UserEntity.builder().email(email).passwordHash(passwordEncoder.encode(password)).role(Role.DOCTOR).build());

        var doctor = DoctorEntity.builder().user(user).firstName(firstName).lastName(lastName).specialization(specialization).delegationConsent(delegationConsent).build();

        return doctorRepository.save(doctor);
    }

    @Transactional(readOnly = true)
    public List<DoctorEntity> findAllDoctors() {
        return doctorRepository.findAll();
    }
}
