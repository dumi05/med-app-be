package com.dc.medapp.medappbe.doctor;

import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorService {
    private final DoctorRepository doctorRepository;
    private final SpecializationRepository specializationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppointmentRepository appointmentRepository;

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
        return doctorRepository.findByUserEnabledTrue();
    }

    @Transactional(readOnly = true)
    public List<DoctorEntity> findAllDoctorsForAdmin() {
        return doctorRepository.findAll();
    }

    @Transactional(readOnly = true)
    public DoctorEntity getDoctorById(Long doctorId) {
        return doctorRepository.findById(doctorId).orElseThrow(() -> new ResourceNotFoundException("Dottore non trovato: " + doctorId));
    }

    @Transactional
    public DoctorEntity updateDoctor(Long doctorId, String firstName, String lastName, Long specializationId, boolean delegationConsent) {
        DoctorEntity doctor = this.getDoctorById(doctorId);
        var specialization = specializationRepository.findById(specializationId)
                .orElseThrow(() -> new ResourceNotFoundException("Specializzazione non trovata: " + specializationId));

        doctor.setFirstName(firstName);
        doctor.setLastName(lastName);
        doctor.setSpecialization(specialization);
        doctor.setDelegationConsent(delegationConsent);
        return doctor;
    }

    @Transactional
    public DoctorEntity deactivate(Long docId) {
        var doctor = this.getDoctorById(docId);
        if(appointmentRepository.existsBySlotDoctorIdAndStatusAndSlotStartTimeAfter(docId, AppointmentStatus.BOOKED, LocalDateTime.now())){
            throw new InvalidStateTransitionException("Il medico ha visite prenotate future: vanno gestite prima della disattivazione.");
        }
        doctor.getUser().setEnabled(false);
        return doctor;
    }

    @Transactional
    public DoctorEntity activate(Long docId) {
        var doctor = this.getDoctorById(docId);
        doctor.getUser().setEnabled(true);
        return doctor;
    }
}
