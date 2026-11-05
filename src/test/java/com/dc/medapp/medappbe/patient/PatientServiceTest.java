package com.dc.medapp.medappbe.patient;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentService;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.audit.AuditLogRepository;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.InvoiceItemRepository;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.reports.MedicalReportRepository;
import com.dc.medapp.medappbe.reports.MedicalReportService;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
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
public class PatientServiceTest {

    private DoctorEntity doc;
    private PatientEntity patient;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private PriceListRepository priceListRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private PatientService patientService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private MedicalReportRepository medicalReportRepository;
    @Autowired
    private InvoiceItemRepository invoiceItemRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;
    @Autowired
    private MedicalReportService medicalReportService;
    @Autowired
    private BillingService billingService;

    @BeforeEach
    void setUp() throws Exception {
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-tes").build());
        priceListRepository.save(PriceListEntity.builder().price(new BigDecimal("100.00")).visitType(BillingService.STANDARD_VISIT_TYPE).specialization(specialization).build());
        var docUser = userRepository.save(UserEntity.builder().role(Role.DOCTOR).email("doc@gmail.com").passwordHash("hash").enabled(true).build());
        doc = doctorRepository.save(DoctorEntity.builder().user(docUser).specialization(specialization).delegationConsent(true).firstName("Gigi").lastName("Arrighi").build());
        var patientUser = userRepository.save(UserEntity.builder().enabled(true).passwordHash(passwordEncoder.encode("password123")).role(Role.PATIENT).email("patient@gmail.com").build());
        patient = patientRepository.save(
                PatientEntity.builder()
                        .birthDate(LocalDate.of(1999, 1, 12))
                        .gdprConsent(true)
                        .gdprConsentTimestamp(Instant.now())
                        .gdprConsentVersion("v1.0")
                        .anonymized(false)
                        .user(patientUser)
                        .firstName("Luigi")
                        .lastName("Longhi")
                        .build());
    }

    @Test
    void anonymize_removesTheIdentifyingData_andSwitchesTheAccountOff() {
        patientService.anonymize(patient.getId());

        assertThat(patient.isAnonymized()).isTrue();
        assertThat(patient.getFirstName()).isEqualTo(PatientService.ANONYMIZED_FIRST_NAME);
        assertThat(patient.getLastName()).isEqualTo(PatientService.ANONYMIZED_LAST_NAME);
        assertThat(patient.getBirthDate()).isNull();
        var user = patient.getUser();
        assertThat(user.getEmail()).isEqualTo("anonymized-"+patient.getId()+"@studio-medico.invalid");
        assertThat(user.isEnabled()).isFalse();
        assertThat(passwordEncoder.matches("password123", user.getPasswordHash())).isFalse();
    }

    @Test
    void anonymize_keepsTheConsentRecord() {
        patientService.anonymize(patient.getId());

        assertThat(patient.isGdprConsent()).isTrue();
        assertThat(patient.getGdprConsentVersion()).isEqualTo("v1.0");
        assertThat(patient.getGdprConsentTimestamp()).isNotNull();
    }

    @Test
    void anonymize_keepsAppointmentsReportsAndInvoices() {
        var invoiced = invoicedAppointment(1);

        patientService.anonymize(patient.getId());

        assertThat(appointmentRepository.findById(invoiced.getId())).isPresent();
        assertThat(medicalReportRepository.findByAppointmentId(invoiced.getId())).isPresent();
        var item = invoiceItemRepository.findAll().stream().filter(i -> i.getAppointment().getId().equals(invoiced.getId())).findFirst().orElseThrow();
        assertThat(item.getAmount()).isEqualByComparingTo("100.00");
        assertThat(invoiced.getPatient().getFirstName()).isEqualTo(PatientService.ANONYMIZED_FIRST_NAME);
    }

    @Test
    void anonymize_cancelsTheVisitsToCome_andFreeTheirSlots() {
        var future = appointmentService.createAppointment(patient.getId(), slot(3).getId());

        patientService.anonymize(patient.getId());

        assertThat(future.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(future.getSlot().isBooked()).isFalse();
    }

    @Test
    void anonymize_leavesAPastBookedAppointmentAlone() {
        var past = appointmentRepository.save(
                AppointmentEntity.builder()
                        .patient(patient)
                        .slot(slot(-2))
                        .status(AppointmentStatus.BOOKED)
                        .noShowRisk(false)
                        .build());
        patientService.anonymize(patient.getId());
        assertThat(past.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void anonymize_throwsInvalidStateTransitionException_whenAlreadyAnonymized() {
        patientService.anonymize(patient.getId());

        assertThrows(InvalidStateTransitionException.class, () -> patientService.anonymize(patient.getId()));
    }

    @Test
    void anonymize_throwsResourceNotFoundException_whenPatientIsNotPresent(){
        assertThrows(ResourceNotFoundException.class, () -> patientService.anonymize(999_999L));
    }

    @Test
    void anonymize_leavesATraceInTheAccessLog() {
        patientService.anonymize(patient.getId());

        assertThat(auditLogRepository.findAll()).anySatisfy(line -> {
            assertThat(line.getAction()).isEqualTo("ANONYMIZE_PATIENT");
            assertThat(line.getEntityId()).isEqualTo(patient.getId());
        });
    }

    @Test
    void exportData_returnsTheProfile_theAppointmentsAndTheReports() {
        invoicedAppointment(1);
        appointmentService.createAppointment(patient.getId(), slot(5).getId());

        var export = patientService.exportData(patient.getId());
        assertThat(export.profile().email()).isEqualTo("patient@gmail.com");
        assertThat(export.profile().firstName()).isEqualTo("Luigi");
        assertThat(export.profile().birthDate()).isEqualTo(LocalDate.of(1999, 1, 12));
        assertThat(export.appointments()).hasSize(2);
        assertThat(export.reports()).hasSize(1);
        assertThat(export.reports().get(0).diagnosisText()).isEqualTo("Nessun risultato.");
    }

    @Test
    void exportData_doesNotMixUpTheDataOfAnotherPatient(){
        invoicedAppointment(1);
        var otherUser = userRepository.save(UserEntity.builder().email("a.patient@gmail.com").passwordHash("pswHash").role(Role.PATIENT).build());
        var other = patientRepository.save(
                PatientEntity.builder()
                        .firstName("Antonio")
                        .lastName("Tonio")
                        .user(otherUser)
                        .anonymized(false)
                        .gdprConsentVersion("v1.0")
                        .gdprConsent(true)
                        .gdprConsentTimestamp(Instant.now())
                .build());
        var export = patientService.exportData(other.getId());

        assertThat(export.appointments()).isEmpty();
        assertThat(export.reports()).isEmpty();
    }

    @Test
    void exportData_leavesATraceInTheAccessLog() {
        patientService.exportData(patient.getId());

        assertThat(auditLogRepository.findAll()).anySatisfy(line -> {
            assertThat(line.getAction()).isEqualTo("EXPORT_PATIENT_DATA");
            assertThat(line.getEntityId()).isEqualTo(patient.getId());
        });
    }

    private AvailabilitySlotEntity slot(int daysFromNow) {
        return availabilitySlotRepository.save(
                AvailabilitySlotEntity.builder()
                        .doctor(doc)
                        .startTime(LocalDateTime.now().plusDays(daysFromNow))
                        .durationMinutes(32)
                        .booked(false)
                        .build()
        );
    }

    private AppointmentEntity invoicedAppointment(int daysFromNow) {
        var appointment = appointmentService.createAppointment(patient.getId(), slot(daysFromNow).getId());
        var done = appointmentService.completeVisit(appointment.getId(), doc.getId());
        billingService.issueInvoiceItem(done);
        medicalReportService.createReport(doc.getUser(), appointment.getId(), "Nessun risultato.", null);
        return appointment;
    }
}
