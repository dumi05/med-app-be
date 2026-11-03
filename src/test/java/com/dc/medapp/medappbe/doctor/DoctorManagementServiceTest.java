package com.dc.medapp.medappbe.doctor;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.patient.PatientEntity;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.schedule.ScheduleService;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.dc.medapp.medappbe.support.ApiTestClient.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class DoctorManagementServiceTest {
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private DoctorService doctorService;
    @Autowired private ScheduleService scheduleService;
    @Autowired private SpecializationRepository specializationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AvailabilitySlotRepository slotRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    private SpecializationEntity cardiology;
    private SpecializationEntity dermatology;
    private DoctorEntity doctor;
    private PatientEntity patient;

    @BeforeEach
    void setUp() {
        this.cardiology = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-test").build());
        this.dermatology = specializationRepository.save(SpecializationEntity.builder().name("Dermatologia-test").build());
        doctor = doctorService.createDoctor("doc-mngm@test.it", PASSWORD, "Giorgio", "Pome", cardiology.getId(), false);
        var patientUsr = userRepository.save(UserEntity.builder().email("pat-test@gotm.is").passwordHash("password9023").role(Role.PATIENT).build());
        this.patient = patientRepository.save(PatientEntity.builder()
                        .lastName("Rocco")
                        .firstName("Roberto")
                        .user(patientUsr)
                        .birthDate(LocalDate.of(1962,5,3))
                        .gdprConsent(true)
                        .gdprConsentTimestamp(Instant.now())
                        .gdprConsentVersion("v1.0")
                        .anonymized(false)
                .build());
    }

    @Test
    void updateDoctor_changesTheEditableFields() {
        var updated = doctorService.updateDoctor(doctor.getId(), "Gigi", "Bianco", dermatology.getId(), true);

        assertThat(updated.getFirstName()).isEqualTo("Gigi");
        assertThat(updated.getLastName()).isEqualTo("Bianco");
        assertThat(updated.getSpecialization().getId()).isEqualTo(dermatology.getId());
        assertThat(updated.isDelegationConsent()).isTrue();
        assertThat(updated.getUser().getEmail()).isEqualTo("doc-mngm@test.it");
    }

    @Test
    void updateDoctor_throwsResourceNotFoundException_whenTheDoctorDoesNotExist() {
        assertThrows(ResourceNotFoundException.class, () -> doctorService.updateDoctor(999_999L, "Gigi", "Bianco", dermatology.getId(), true));
    }

    @Test
    void updateDoctor_throwsResourceNotFoundException_whenTheSpecializationDoesNotExist(){
        assertThrows(ResourceNotFoundException.class, () -> doctorService.updateDoctor(doctor.getId(), "Gigi", "Riva", 999_999L, true));
    }

    @Test
    void deactivate_disablesTheAccount() {
        var deactivated = doctorService.deactivate(doctor.getId());

        assertThat(deactivated.getUser().isEnabled()).isFalse();
    }

    @Test
    void deactivate_throwsInvalidStateTransitionException_whenTheDoctorHasUpcomingBookedVisits(){
        saveAppointment(LocalDateTime.now().plusDays(5), AppointmentStatus.BOOKED);

        assertThrows(InvalidStateTransitionException.class, () -> doctorService.deactivate(doctor.getId()));
        assertThat(doctor.getUser().isEnabled()).isTrue();
    }

    @Test
    void deactivate_succeeds_whenTheOnlyVisitsArePastOrCancelled() {
        saveAppointment(LocalDateTime.now().minusDays(2), AppointmentStatus.COMPLETED);
        saveAppointment(LocalDateTime.now().minusDays(1), AppointmentStatus.BOOKED);
        saveAppointment(LocalDateTime.now().plusDays(3), AppointmentStatus.CANCELLED);

        var deactivated = doctorService.deactivate(doctor.getId());

        assertThat(deactivated.getUser().isEnabled()).isFalse();
    }

    @Test
    void activate_enablesTheAccountAgain() {
        doctorService.deactivate(doctor.getId());
        var activated = doctorService.activate(doctor.getId());
        assertThat(activated.getUser().isEnabled()).isTrue();
    }

    @Test
    void findAllDoctors_excludesDeactivatedDoctors_whileTheAdminListKeepsThem() {
        doctorService.deactivate(doctor.getId());

        assertThat(doctorService.findAllDoctors()).extracting(DoctorEntity::getId).doesNotContain(doctor.getId());
        assertThat(doctorService.findAllDoctorsForAdmin()).extracting(DoctorEntity::getId).contains(doctor.getId());
    }

    @Test
    void searchAvailability_excludesTheSlotsOfDeactivatedDoctors() {
        var startTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        var slot = slotRepository.save(AvailabilitySlotEntity.builder().booked(false).durationMinutes(30).doctor(doctor).startTime(startTime).build());
        assertThat(scheduleService.searchAvailability(cardiology.getId(), null, startTime.toLocalDate(), startTime.toLocalDate()))
                .extracting(AvailabilitySlotEntity::getId).contains(slot.getId());

        doctorService.deactivate(doctor.getId());

        assertThat(scheduleService.searchAvailability(cardiology.getId(), null, startTime.toLocalDate(), startTime.toLocalDate())).isEmpty();
    }

    private void saveAppointment(LocalDateTime startTime, AppointmentStatus status){
        var slot = slotRepository.save(AvailabilitySlotEntity.builder().startTime(startTime).doctor(doctor).durationMinutes(30).booked(true).build());
        appointmentRepository.save(AppointmentEntity.builder().slot(slot).status(status).noShowRisk(false).patient(patient).build());
    }
}
