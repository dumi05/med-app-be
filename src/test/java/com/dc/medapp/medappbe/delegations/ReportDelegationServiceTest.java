package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.appointments.AppointmentService;
import com.dc.medapp.medappbe.audit.AuditLogRepository;
import com.dc.medapp.medappbe.audit.AuditOutcome;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.patient.PatientEntity;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.patient.PatientService;
import com.dc.medapp.medappbe.reports.MedicalReportService;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class ReportDelegationServiceTest {
    @Autowired private ReportDelegationService service;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private ReportDelegationRepository reportDelegationRepository;
    @Autowired private PatientService patientService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private MedicalReportService medicalReportService;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SpecializationRepository specializationRepository;
    @Autowired private AvailabilitySlotRepository slotRepository;
    @Autowired private PriceListRepository priceListRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private PatientEntity patient;
    private DoctorEntity doctor;

    @BeforeEach
    void setUp() {
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Dermatologia-test").build());
        priceListRepository.save(
                PriceListEntity.builder()
                        .specialization(specialization)
                        .visitType(BillingService.STANDARD_VISIT_TYPE)
                        .price(new BigDecimal("100.00"))
                        .build());
        var doctorUser = userRepository.save(UserEntity.builder().email("doc.user@email.com").passwordHash("password123").role(Role.DOCTOR).build());
        doctor = doctorRepository.save(
                DoctorEntity.builder()
                        .user(doctorUser)
                        .firstName("Luigi")
                        .lastName("Mero")
                        .specialization(specialization)
                        .delegationConsent(true)
                        .build());
        patient = createPatient("patient@test.com");
        var appointment = appointmentService.createAppointment(patient.getId(), slot(1).getId());
        appointmentService.completeVisit(appointment.getId(), doctor.getId());
        medicalReportService.createReport(doctor.getUser(), appointment.getId(), "Nessun risultato.", null);
    }

    @Test
    void create_generateAnActiveDelegationWithAToken() {
        var delegation = service.create(patient.getId(), "Luigi Rossi", "luigi@example.com", null);

        assertThat(delegation.getId()).isNotNull();
        assertThat(delegation.getToken()).isNotBlank();
        assertThat(delegation.isRevoked()).isFalse();
        assertThat(delegation.isActive(LocalDate.now())).isTrue();
        assertThat(delegation.getCreatedBy()).isNotBlank();
    }

    @Test
    void create_generatesADifferentTokenEachTime() {
        var first = service.create(patient.getId(), "Luigi Rossi", "luigi@example.it", null);
        var second = service.create(patient.getId(), "Luigi Rossi", "luigi@example.it", null);

        assertThat(first.getToken()).isNotEqualTo(second.getToken());
    }

    @Test
    void create_throwsIllegalArgumentException_whenTheExpiryIsInThePast() {
        assertThrows(IllegalArgumentException.class, () -> service.create(patient.getId(), "Luigi Rossi", "luigi@example.it", LocalDate.now().minusDays(1)));
    }

    @Test
    void create_throwsResourceNotFoundException_whenAPatientDoesNotExists() {
        assertThrows(ResourceNotFoundException.class, () -> service.create(999_999L, "Luigi Rossi", "luigi@example.it", null));
    }

    @Test
    void openByToken_returnTheReportsOfThePatient() {
        var delegation = service.create(patient.getId(), "Luigi Rossi", "luigi@example.it", null);
        var delegationOpened = service.openByToken(delegation.getToken());
        assertThat(service.reportsOf(delegationOpened)).hasSize(1);
    }

    @Test
    void reportsOf_doesNotReturnTheReportOfAnotherPatient() {
        var other = createPatient("other-patient@test.it");
        var delegation = service.create(other.getId(), "Gigi Neri", "gigi@neri.com", null);
        assertThat(service.reportsOf(service.openByToken(delegation.getToken()))).isEmpty();
    }

    @Test
    void openByToken_works_untilTheEndOfTheExpiryDate() {
        var delegation = service.create(patient.getId(), "Luigi Rossi", "luigi@example.it", LocalDate.now());
        assertThat(service.openByToken(delegation.getToken())).isNotNull();
    }

    @Test
    void openByToken_throwsResourceNotFoundException_whnTheTokenIsUnknown() {
        assertThrows(ResourceNotFoundException.class, () -> service.openByToken("not-a-token"));
    }

    @Test
    void openByToken_throwsResourceNotFoundException_whenDelegationIsREvoked() {
        var delegation = service.create(patient.getId(), "Luigi Rossi", "luigi@example.it", null);
        service.revoke(delegation.getId(), patient.getId());

        assertThrows(ResourceNotFoundException.class, () -> service.openByToken(delegation.getToken()));
    }

    @Test
    void openByToken_throwsResourceNotFoundException_whenDelegationIsExpired() {
        var expired = reportDelegationRepository.save(ReportDelegationEntity.builder()
                        .patient(patient)
                        .delegateName("Luigi Rossi")
                        .delegateEmail("luigi@example.com")
                        .token("expired-token")
                        .expiresAt(LocalDate.now().minusDays(1))
                        .revoked(false)
                .build());

        assertThrows(ResourceNotFoundException.class, () -> service.openByToken(expired.getToken()));
    }

    @Test
    void openByToken_writesTheDelegationIdInTheAccessLog_whenItSucceeds() {
        var delegation = service.create(patient.getId(), "Luigi Rossi", "luigi@example.it", null);

        service.openByToken(delegation.getToken());

        assertThat(auditLogRepository.findAll()).anySatisfy(line -> {
            assertThat(line.getAction()).isEqualTo("ACCESS_REPORTS_VIA_DELEGATION");
            assertThat(line.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
            assertThat(line.getEntityId()).isEqualTo(delegation.getId());
        });
    }

    @Test
    void openByToken_writesTheFailureInTheLogs_whenTokenIsNotValid() {
        assertThrows(ResourceNotFoundException.class, () -> service.openByToken("not-a-token"));

        assertThat(auditLogRepository.findAll()).anySatisfy(line -> {
            assertThat(line.getAction()).isEqualTo("ACCESS_REPORTS_VIA_DELEGATION");
            assertThat(line.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        });
    }

    @Test
    void revoke_marksTheDelegationAsRevoked() {
        var delegation = service.create(patient.getId(), "Luigi Rossi", "luigi@example.com", null);

        var revoked = service.revoke(delegation.getId(), patient.getId());

        assertThat(revoked.isRevoked()).isTrue();
        assertThat(revoked.isActive(LocalDate.now())).isFalse();
    }

    @Test
    void revoke_twice_isNotAnError() {
        var delegation = service.create(patient.getId(), "Luigi Rossi", "luigi@example.com", null);
        service.revoke(delegation.getId(), patient.getId());

        assertThat(service.revoke(delegation.getId(), patient.getId()).isRevoked()).isTrue();
    }

    @Test
    void revoke_throwsUnauthorizedAccessException_whenTheDelegationBelongsToAnotherPatient() {
        var delegation = service.create(patient.getId(), "Luigi Rossi", "luigi@example.com", null);
        var other = createPatient("repdel.intruder@test.it");

        assertThrows(UnauthorizedAccessException.class, () -> service.revoke(delegation.getId(), other.getId()));
        assertThat(delegation.isRevoked()).isFalse();
    }

    @Test
    void revoke_throwsResourceNotFoundException_whenTheDelegationDoesNotExist() {
        assertThrows(ResourceNotFoundException.class, () -> service.revoke(999_999L, patient.getId()));
    }

    @Test
    void findByPatient_returnsOnlyTheDelegationsOfThatPatient_newestFirst() {
        var first = service.create(patient.getId(), "Luigi Rossi", "luigi@example.com", null);
        var second = service.create(patient.getId(), "Anna Rossi", "anna@example.com", null);
        service.create(createPatient("repdel.other2@test.it").getId(), "Altro", "altro@example.com", null);

        assertThat(service.findByPatient(patient.getId())).extracting(ReportDelegationEntity::getId)
                .containsExactly(second.getId(), first.getId());
    }

    // ---- erasure --------------------------------------------------------------------------------

    @Test
    void anonymizingThePatient_revokesAllTheirDelegations() {
        var first = service.create(patient.getId(), "Luigi Rossi", "luigi@example.com", null);
        var second = service.create(patient.getId(), "Anna Rossi", "anna@example.com", null);

        patientService.anonymize(patient.getId());

        assertThat(first.isRevoked()).isTrue();
        assertThat(second.isRevoked()).isTrue();
        assertThrows(ResourceNotFoundException.class, () -> service.openByToken(first.getToken()));
    }

    // Helpers

    private AvailabilitySlotEntity slot(int daysFromNow) {
        return slotRepository.save(AvailabilitySlotEntity.builder()
                .doctor(doctor).startTime(LocalDateTime.now().plusDays(daysFromNow)).durationMinutes(30).booked(false).build());
    }

    private PatientEntity createPatient(String email) {
        var user = userRepository.save(UserEntity.builder().email(email).passwordHash("hash").role(Role.PATIENT).build());
        return patientRepository.save(PatientEntity.builder().user(user).firstName("Antonio").lastName("Tonio")
                .gdprConsent(true).gdprConsentTimestamp(Instant.now()).gdprConsentVersion("v1.0").anonymized(false).build());
    }
}
