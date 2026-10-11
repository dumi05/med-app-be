package com.dc.medapp.medappbe.appointments;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.SlotNotAvailableException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
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

    private SpecializationEntity specialization;
    private Long doctorId;
    private Long patientId;
    private Long slotId;

    @BeforeEach
    void setUp() {
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-test").build());
        var doctor = createDoctor("doctor@email.com");
        doctorId = doctor.getId();

        slotId = availabilitySlotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).startTime(LocalDateTime.now().plusDays(1)).durationMinutes(30).booked(false).build()).getId();
        var patient = createPatient("patient@mail.te");
        patientId = patient.getId();
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
    void createAppointment_throwsSlotNotAvailableException_whenSlotIsInThePast(){
        var doctor = doctorRepository.findById(doctorId).orElseThrow();
        var pastSlot = availabilitySlotRepository.save(
                AvailabilitySlotEntity.builder()
                        .doctor(doctor)
                        .startTime(LocalDateTime.now().minusHours(1))
                        .durationMinutes(30)
                        .booked(false)
                        .build()
        );

        assertThrows(SlotNotAvailableException.class, () -> service.createAppointment(patientId, pastSlot.getId()));
    }

    @Test
    void completeVisit_throwsUnauthorizedAccessException_whenDoctorIsNotAssignedToTheSlot(){
        var appointment = service.createAppointment(patientId, slotId);
        var otherDoctorId = createDoctor("other.doc@test.lo").getId();

        assertThrows(UnauthorizedAccessException.class, () -> service.completeVisit(appointment.getId(), otherDoctorId));
    }

    @Test
    void completeVisit_throwsInvalidStateTransitionException_whenAlreadyCompleted(){
        var appointment = service.createAppointment(patientId, slotId);
        service.completeVisit(appointment.getId(), doctorId);
        assertThrows(InvalidStateTransitionException.class, () -> service.completeVisit(appointment.getId(), doctorId));
    }

    @Test
    void cancelAppointment_freeTheSlot() {
        var appointment = service.createAppointment(patientId, slotId);

        var cancelled = service.cancelAppointment(appointment.getId(), patientId);

        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(availabilitySlotRepository.findById(slotId).orElseThrow().isBooked()).isFalse();
    }

    @Test
    void cancelAppointment_throwsUnauthorizedAccessException_whenPatientIsNotTheOwner(){
        var appointment = service.createAppointment(patientId, slotId);
        var otherPatientId = createPatient("onother@patient.com").getId();

        assertThrows(UnauthorizedAccessException.class, () -> service.cancelAppointment(appointment.getId(), otherPatientId));
        assertThat(availabilitySlotRepository.findById(slotId).orElseThrow().isBooked()).isTrue();
    }

    @Test
    void createAppointment_succeeds_whenSameSlotIsBookedAgainAfterCancellation() {
        var first = service.createAppointment(patientId, slotId);
        service.cancelAppointment(first.getId(), patientId);

        var second = service.createAppointment(patientId, slotId);

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void completeVisit_throwsResourceNotFoundException_whenAppointmentDoesNotExist(){
        assertThrows(ResourceNotFoundException.class, () -> service.completeVisit(999_999L, doctorId));
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

    @Test
    void findByPatient_returnsOnlyOwnAppointmentsOrderedByStartTime(){
        var doctor = doctorRepository.findById(doctorId).orElseThrow();
        var laterSlot = availabilitySlotRepository.save(
                AvailabilitySlotEntity.builder()
                        .doctor(doctor)
                        .startTime(LocalDateTime.now().plusDays(3))
                        .durationMinutes(30)
                        .booked(false)
                        .build()
        );
        var otherSlot = availabilitySlotRepository.save(
                AvailabilitySlotEntity.builder()
                        .doctor(doctor)
                        .startTime(LocalDateTime.now().plusDays(4))
                        .durationMinutes(30)
                        .booked(false)
                        .build()
        );
        var otherPatientId = createPatient("patient@2p.com").getId();
        var later = service.createAppointment(patientId, laterSlot.getId());
        var earlier = service.createAppointment(patientId, slotId);
        service.createAppointment(otherPatientId, otherSlot.getId());

        var mine = service.findByPatient(patientId);

        assertThat(mine).extracting(AppointmentEntity::getId).containsExactly(earlier.getId(), later.getId());
    }

    @Test
    void createAppointment_incrementsSlotVersion(){
        var slot = availabilitySlotRepository.findById(slotId).orElseThrow();
        long versionBefore = slot.getVersion();

        service.createAppointment(patientId, slotId);
        availabilitySlotRepository.flush();

        assertThat(slot.getVersion()).isGreaterThan(versionBefore);
    }

    private DoctorEntity createDoctor(String email){
        var doctorUser = userRepository.save(UserEntity.builder().email(email).passwordHash("hash").role(Role.DOCTOR).build());
        return doctorRepository.save(DoctorEntity.builder().user(doctorUser).firstName("Giovanni").lastName("Latorre").specialization(specialization).build());
    }

    private PatientEntity createPatient(String email){
        var patientUser = userRepository.save(UserEntity.builder().role(Role.PATIENT).email(email).passwordHash("hash2").build());
        return patientRepository.save(PatientEntity.builder().firstName("Chiara").lastName("Aiello").user(patientUser).gdprConsent(true).gdprConsentTimestamp(Instant.now()).gdprConsentVersion("v1.0").anonymized(false).build());
    }
}
