package com.dc.medapp.medappbe.appointments;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.SlotNotAvailableException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class AppointmentServiceTest {
    @Autowired
    private AppointmentService service;
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

    private Long patientId;
    private Long slotId;

    @BeforeEach
    void setUp() {
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-test").build());
        var doctorUser = userRepository.save(UserEntity.builder().email("doctor@email.com").passwordHash("hash").role(Role.DOCTOR).build());
        var doctor = doctorRepository.save(DoctorEntity.builder().user(doctorUser).firstName("Giovanni").lastName("Latorre").specialization(specialization).build());

        slotId = availabilitySlotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).startTime(LocalDateTime.now().plusDays(1)).durationMinutes(30).booked(false).build()).getId();
        var patientUser = userRepository.save(UserEntity.builder().role(Role.PATIENT).email("patient@mail.te").passwordHash("hash2").build());
        patientId = patientRepository.save(PatientEntity.builder().firstName("Chiara").lastName("Aiello").user(patientUser).gdprConsent(true).gdprConsentTimestamp(Instant.now()).gdprConsentVersion("v1.0").anonymized(false).build()).getId();
    }

    @Test
    void createAppointment_setStatusBookedAndMarksSlotAsBooked(){
        var appointment = service.createAppointment(patientId, slotId);

        assertThat(appointment.getId()).isNotNull();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(availabilitySlotRepository.findById(slotId).orElseThrow().isBooked()).isTrue();
    }

    @Test
    void createAppointment_throwsSlotNotAvailableException_whenSlotAlreadyBooked(){
        service.createAppointment(patientId, slotId);

        assertThrows(SlotNotAvailableException.class, () -> service.createAppointment(patientId, slotId));
    }

    @Test
    void completeVisit_throwsInvalidStateTransitionException_whenAlreadyCompleted(){
        var appointment = service.createAppointment(patientId, slotId);
        service.completeVisit(appointment.getId());
        assertThrows(InvalidStateTransitionException.class, () -> service.completeVisit(appointment.getId()));
    }

    @Test
    void cancelAppointment_freeTheSlot() {
        var appointment = service.createAppointment(patientId, slotId);

        var cancelled = service.cancelAppointment(appointment.getId());

        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(availabilitySlotRepository.findById(slotId).orElseThrow().isBooked()).isFalse();
    }

    @Test
    void createAppointment_succeeds_whenSameSlotIsBookedAgainAfterCancellation() {
        var first = service.createAppointment(patientId, slotId);
        service.cancelAppointment(first.getId());

        var second = service.createAppointment(patientId, slotId);

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void completeVisit_throwsResourceNotFoundException_whenAppointmentDoesNotExist(){
        assertThrows(ResourceNotFoundException.class, () -> service.completeVisit(999_999L));
    }

    @Test
    void createAppointment_setsNoShowRisk_whenPatientMissedPastAppointments() {
        var doctor = availabilitySlotRepository.findById(slotId).orElseThrow().getDoctor();
        var patient = patientRepository.findById(patientId).orElseThrow();

        for(int i = 0; i < 2; i++) {
            var pastSlot = availabilitySlotRepository.save(
                    AvailabilitySlotEntity.builder()
                            .doctor(doctor)
                            .startTime(LocalDateTime.now().minusDays(5+i))
                            .durationMinutes(30)
                            .booked(true)
                            .build()
            );
            appointmentRepository.save(
                    AppointmentEntity.builder()
                            .patient(patient)
                            .slot(pastSlot)
                            .status(AppointmentStatus.BOOKED)
                            .noShowRisk(false)
                            .build()
            );
        }

        var appointment = service.createAppointment(patientId, slotId);

        assertThat(appointment.isNoShowRisk()).isTrue();
    }
}
