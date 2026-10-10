package com.dc.medapp.medappbe.appointments;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.doctor.SpecializationEntity;
import com.dc.medapp.medappbe.doctor.SpecializationRepository;
import com.dc.medapp.medappbe.patient.PatientEntity;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class NoShowRiskCalculatorTest {
    @Autowired
    private NoShowRiskCalculator calculator;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private UserRepository userRepository;

    private DoctorEntity doc;
    private PatientEntity patient;

    @BeforeEach
    void setUp(){
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-test").build());
        var doctorUser = userRepository.save(UserEntity.builder().email("doctor@email.com").passwordHash("hash").role(Role.DOCTOR).build());
        doc = doctorRepository.save(DoctorEntity.builder().user(doctorUser).firstName("Giovanni").lastName("Latorre").specialization(specialization).build());
        var patientUser = userRepository.save(UserEntity.builder().role(Role.PATIENT).email("patient@mail.te").passwordHash("hash2").build());
        patient = patientRepository.save(PatientEntity.builder().firstName("Chiara").lastName("Aiello").user(patientUser).gdprConsent(true).gdprConsentTimestamp(Instant.now()).gdprConsentVersion("v1.0").anonymized(false).build());
    }

    @Test
    void calculateRisk_returnsFalse_whenPatientHasNoHistory(){
        assertThat(calculator.calculateRisk(patient.getId())).isFalse();
    }

    @Test
    void calculateRisk_returnsTrue_whenAllPastAppointmentsAreStillBooked() {
        savePastAppointment(AppointmentStatus.BOOKED);
        savePastAppointment(AppointmentStatus.BOOKED);

        assertThat(calculator.calculateRisk(patient.getId())).isTrue();
    }

    @Test
    void calculateRisk_returnsTrue_whenExactlyHalfOfPastAppointmentsAreMissing(){
        savePastAppointment(AppointmentStatus.BOOKED);
        savePastAppointment(AppointmentStatus.COMPLETED);

        assertThat(calculator.calculateRisk(patient.getId())).isTrue();
    }

    @Test
    void calculateRisk_returnsFalse_whenMissedAppointmentsAreBelowThreshold(){
        savePastAppointment(AppointmentStatus.COMPLETED);
        savePastAppointment(AppointmentStatus.COMPLETED);
        savePastAppointment(AppointmentStatus.BOOKED);

        assertThat(calculator.calculateRisk(patient.getId())).isFalse();
    }

    @Test
    void calculateRisk_ignoreCancelledAppointments(){
        savePastAppointment(AppointmentStatus.BOOKED);
        savePastAppointment(AppointmentStatus.COMPLETED);
        savePastAppointment(AppointmentStatus.CANCELLED);
        savePastAppointment(AppointmentStatus.CANCELLED);

        assertThat(calculator.calculateRisk(patient.getId())).isTrue();
    }

    @Test
    void calculateRisk_ignoresFutureAppointments(){
        saveAppointment(LocalDateTime.now().plusDays(3), AppointmentStatus.BOOKED);
        assertThat(calculator.calculateRisk(patient.getId())).isFalse();
    }

    private void savePastAppointment(AppointmentStatus status){
        saveAppointment(LocalDateTime.now().minusDays(5), status);
    }

    private void saveAppointment(LocalDateTime startTime, AppointmentStatus status){
        var slot = availabilitySlotRepository.save(AvailabilitySlotEntity.builder().doctor(doc).startTime(startTime).durationMinutes(30).booked(true).build());
        appointmentRepository.save(AppointmentEntity.builder().patient(patient).slot(slot).status(status).noShowRisk(false).build());
    }
}

