package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentService;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.InvoiceItemRepository;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.patient.PatientEntity;
import com.dc.medapp.medappbe.patient.PatientRepository;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class MedicalReportServiceTest {

    @Autowired
    private MedicalReportService reportService;
    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private AvailabilitySlotRepository slotRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PriceListRepository priceListRepository;
    @Autowired
    private InvoiceItemRepository invoiceItemRepository;
    @Autowired
    private BillingService billingService;

    private SpecializationEntity specialization;
    private DoctorEntity doctor;
    private PatientEntity patient;
    private AppointmentEntity completedAppointment;

    @BeforeEach
    void setUp() {
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-report-test").build());
        doctor = createDoctor("report.doctor@test.it");
        patient = createPatient("report.patient@test.it");

        priceListRepository.save(PriceListEntity.builder()
                .specialization(specialization)
                .visitType(BillingService.STANDARD_VISIT_TYPE)
                .price(new BigDecimal("100.00"))
                .build());

        var booked = bookAppointment(patient, doctor, 1);
        completedAppointment = appointmentService.completeVisit(booked.getId(), doctor.getId());
        billingService.issueInvoiceItem(completedAppointment);
    }

    @Test
    void createReport_storesTheReport_andTheAppointmentBecomesReported() {
        var report = reportService.createReport(doctor.getUser(), completedAppointment.getId(), "Nessun risultato.", null);

        assertThat(report.getId()).isNotNull();
        assertThat(report.getDiagnosisText()).isEqualTo("Nessun risultato.");
        assertThat(report.getCompletedAt()).isNotNull();
        assertThat(completedAppointment.getStatus()).isEqualTo(AppointmentStatus.REPORTED);
        assertThat(completedAppointment.getReportedAt()).isNotNull();
        assertThat(completedAppointment.getInvoicedAt()).isNotNull();
    }

    @Test
    void createReport_throwsInvalidStateTransitionException_whenAppointmentIsNotCompleted() {
        var stillBooked = bookAppointment(patient, doctor, 2);

        assertThrows(InvalidStateTransitionException.class,
                () -> reportService.createReport(doctor.getUser(), stillBooked.getId(), "Nessun risultato.", null));
    }

    @Test
    void createReport_throwsInvalidStateTransitionException_whenReportAlreadyWritten() {
        reportService.createReport(doctor.getUser(), completedAppointment.getId(), "Nessun risultato.", null);

        assertThrows(InvalidStateTransitionException.class,
                () -> reportService.createReport(doctor.getUser(), completedAppointment.getId(), "Ancora", null));
    }

    @Test
    void createReport_throwsUnauthorizedAccessException_whenDoctorIsNotTheAssignedOne() {
        var colleague = createDoctor("collega@test.it");

        assertThrows(UnauthorizedAccessException.class,
                () -> reportService.createReport(colleague.getUser(), completedAppointment.getId(), "Nessun risultato.", null));
    }

    @Test
    void createReport_throwsUnauthorizedAccessException_whenActorIsThePatient() {
        assertThrows(UnauthorizedAccessException.class,
                () -> reportService.createReport(patient.getUser(), completedAppointment.getId(), "Nessun risultato.", null));
    }

    @Test
    void createReport_throwsResourceNotFoundException_whenAppointmentDoesNotExist() {
        assertThrows(ResourceNotFoundException.class,
                () -> reportService.createReport(doctor.getUser(), 999_999L, "Nessun risultato.", null));
    }

    @Test
    void getReport_returnsTheReport_toTheOwnerPatient() {
        var report = reportService.createReport(doctor.getUser(), completedAppointment.getId(), "Nessun risultato.", null);

        assertThat(reportService.getReport(patient.getUser(), report.getId()).getId()).isEqualTo(report.getId());
    }

    @Test
    void getReport_returnsTheReport_toTheAssignedDoctor() {
        var report = reportService.createReport(doctor.getUser(), completedAppointment.getId(), "Nessun risultato.", null);

        assertThat(reportService.getReport(doctor.getUser(), report.getId()).getId()).isEqualTo(report.getId());
    }

    @Test
    void getReport_throwsUnauthorizedAccessException_toAnotherPatient() {
        var report = reportService.createReport(doctor.getUser(), completedAppointment.getId(), "Nessun risultato.", null);
        var otherPatient = createPatient("altro.patient@test.it");

        assertThrows(UnauthorizedAccessException.class,
                () -> reportService.getReport(otherPatient.getUser(), report.getId()));
    }

    @Test
    void getReport_throwsUnauthorizedAccessException_toAnotherDoctor() {
        var report = reportService.createReport(doctor.getUser(), completedAppointment.getId(), "Nessun risultato.", null);
        var colleague = createDoctor("collega@test.it");

        assertThrows(UnauthorizedAccessException.class,
                () -> reportService.getReport(colleague.getUser(), report.getId()));
    }

    @Test
    void getReport_throwsUnauthorizedAccessException_toTheAdmin() {
        var report = reportService.createReport(doctor.getUser(), completedAppointment.getId(), "Nessun risultato.", null);
        var admin = userRepository.save(UserEntity.builder().email("admin.report@test.it").passwordHash("password").role(Role.ADMIN).build());

        assertThrows(UnauthorizedAccessException.class,
                () -> reportService.getReport(admin, report.getId()));
    }

    @Test
    void getReport_throwsResourceNotFoundException_whenReportDoesNotExist() {
        assertThrows(ResourceNotFoundException.class,
                () -> reportService.getReport(patient.getUser(), 999_999L));
    }

    @Test
    void getReportByAppointment_returnsTheReport_toTheOwnerPatient() {
        var report = reportService.createReport(doctor.getUser(), completedAppointment.getId(), "Nessun risultato.", null);

        var found = reportService.getReportByAppointment(patient.getUser(), completedAppointment.getId());

        assertThat(found.getId()).isEqualTo(report.getId());
    }

    @Test
    void getReportByAppointment_throwsResourceNotFoundException_whenNoReportYet() {
        assertThrows(ResourceNotFoundException.class,
                () -> reportService.getReportByAppointment(patient.getUser(), completedAppointment.getId()));
    }

    @Test
    void createReport_throwsInvalidStateTransitionException_whenThePaymentWasNotConfirmedYet(){
        var booked = bookAppointment(patient, doctor, 4);
        var completedOnly = appointmentService.completeVisit(booked.getId(),doctor.getId());

        assertThrows(InvalidStateTransitionException.class, () -> reportService.createReport(doctor.getUser(), completedOnly.getId(), "Nessun risultato.", null));
        assertThat(completedOnly.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }
    @Test
    void createReport_doesNotNeedThePriceAnymore_theInvoiceWasIssuedBefore() {
        priceListRepository.deleteAll();

        var report = reportService.createReport(doctor.getUser(), completedAppointment.getId(), "Nessun risultato.", null);

        assertThat(report.getId()).isNotNull();
    }

    private AppointmentEntity bookAppointment(PatientEntity forPatient, DoctorEntity withDoctor, int daysFromNow) {
        var slot = slotRepository.save(AvailabilitySlotEntity.builder()
                .doctor(withDoctor)
                .startTime(LocalDateTime.now().plusDays(daysFromNow))
                .durationMinutes(30)
                .booked(false)
                .build());
        return appointmentService.createAppointment(forPatient.getId(), slot.getId());
    }

    private DoctorEntity createDoctor(String email) {
        var user = userRepository.save(UserEntity.builder().email(email).passwordHash("spwhash").role(Role.DOCTOR).build());
        return doctorRepository.save(DoctorEntity.builder().user(user).firstName("Giovanni").lastName("Latorre").specialization(specialization).build());
    }

    private PatientEntity createPatient(String email) {
        var user = userRepository.save(UserEntity.builder().email(email).passwordHash("passwordhash").role(Role.PATIENT).build());
        return patientRepository.save(PatientEntity.builder().user(user).firstName("Antonio").lastName("Tonio")
                .gdprConsent(true).gdprConsentTimestamp(Instant.now()).gdprConsentVersion("v1.0").anonymized(false).build());
    }
}