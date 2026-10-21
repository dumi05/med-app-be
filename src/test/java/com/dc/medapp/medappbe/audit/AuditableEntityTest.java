package com.dc.medapp.medappbe.audit;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.patient.PatientEntity;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.security.CurrentUser;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AuditableEntityTest {
    @Autowired
    private AppointmentRepository appointmentRepo;
    @Autowired
    private AvailabilitySlotRepository slotRepository;
    @Autowired
    private DoctorRepository doctorRepo;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private PatientRepository patientRepo;
    @Autowired
    private SpecializationRepository specializationRepository;

    private UserEntity patientUser;
    private UserEntity doctorUser;
    private PatientEntity patient;
    private AvailabilitySlotEntity slot;

    public static final String AUDITABLE_PATIENT_MAIL = "auditable@patient.com";
    public static final String AUDITABLE_DOC_MAIL = "auditable@test.doc";

    @BeforeEach
    void setUpd() {
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-test").build());
        doctorUser = userRepo.save(UserEntity.builder().role(Role.DOCTOR).passwordHash("hash").email(AUDITABLE_DOC_MAIL).build());
        var doctor = doctorRepo.save(DoctorEntity.builder().user(doctorUser).lastName("Pippo").firstName("Carlo").delegationConsent(true).specialization(specialization).build());
        slot = slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).startTime(LocalDateTime.now().plusDays(7)).durationMinutes(50).booked(true).build());
        patientUser = userRepo.save(UserEntity.builder().role(Role.PATIENT).email(AUDITABLE_PATIENT_MAIL).passwordHash("hash").build());
        patient = patientRepo.save(PatientEntity.builder().user(patientUser).lastName("Pluto").anonymized(false).gdprConsentVersion("v1.0").gdprConsentTimestamp(Instant.now()).gdprConsent(true).birthDate(LocalDate.of(1985, 5, 26)).build());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void save_recordsWhenAndByWhomTheRowWasCreated() {
        actAs(patientUser);

        var appointment = appointmentRepo.saveAndFlush(newAppointment());

        assertThat(appointment.getCreatedAt()).isNotNull();
        assertThat(appointment.getUpdatedAt()).isNotNull();
        assertThat(appointment.getCreatedBy()).isEqualTo(AUDITABLE_PATIENT_MAIL);
        assertThat(appointment.getUpdatedBy()).isEqualTo(AUDITABLE_PATIENT_MAIL);
    }

    @Test
    void update_changeOnlyTheLastModifiedColumns() {
        actAs(patientUser);
        var appointment = appointmentRepo.saveAndFlush(newAppointment());

        actAs(doctorUser);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepo.saveAndFlush(appointment);

        assertThat(appointment.getCreatedBy()).isEqualTo(AUDITABLE_PATIENT_MAIL);
        assertThat(appointment.getUpdatedBy()).isEqualTo(AUDITABLE_DOC_MAIL);
        assertThat(appointment.getUpdatedAt()).isAfterOrEqualTo(appointment.getCreatedAt());
    }

    @Test
    void save_withoutAuthenticatedUser_recordsTheSystem() {
        var appointment = appointmentRepo.saveAndFlush(newAppointment());

        assertThat(appointment.getCreatedBy()).isEqualTo(CurrentUser.SYSTEM);
        assertThat(appointment.getUpdatedBy()).isEqualTo(CurrentUser.SYSTEM);
    }

    private AppointmentEntity newAppointment() {
        return AppointmentEntity.builder().patient(patient).slot(slot).status(AppointmentStatus.BOOKED).noShowRisk(false).build();
    }

    private void actAs(UserEntity user){
        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_"+user.getRole().name()))
                        )
                );
    }
}
