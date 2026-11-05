package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentService;
import com.dc.medapp.medappbe.audit.AuditLogRepository;
import com.dc.medapp.medappbe.audit.AuditOutcome;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.common.exception.FileTooLargeException;
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
import com.dc.medapp.medappbe.support.TestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class MedicalReportAttachmentServiceTest {

    @Autowired
    private MedicalReportService reportService;
    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private MedicalReportRepository reportRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
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
    private BillingService billingService;
    @Value("${app.storage.dir}")
    private String storageDir;

    private SpecializationEntity specialization;
    private DoctorEntity doctor;
    private PatientEntity patient;
    private AppointmentEntity completed;
    private long filesBefore;

    @BeforeEach
    void setUp() {
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-att-service-test").build());
        priceListRepository.save(PriceListEntity.builder()
                .specialization(specialization).visitType(BillingService.STANDARD_VISIT_TYPE).price(new BigDecimal("100.00")).build());
        doctor = createDoctor("att.service.doctor@test.it");
        patient = createPatient("att.service.patient@test.it");
        completed = completedVisit(patient, doctor, 1);
        filesBefore = filesOnTheDisk();
    }

    // ---- upload ---------------------------------------------------------------------------------

    @Test
    void createReport_withAPdf_storesTheFileAndItsTypeAndSize() throws IOException {
        var report = reportService.createReport(doctor.getUser(), completed.getId(), "Nessun risultato.", TestFiles.pdf());

        assertThat(report.hasAttachment()).isTrue();
        assertThat(report.getAttachmentContentType()).isEqualTo("application/pdf");
        assertThat(report.getAttachmentSize()).isEqualTo(TestFiles.pdfBytes().length);
        var download = reportService.downloadOf(report);
        assertThat(download.resource().getInputStream().readAllBytes()).isEqualTo(TestFiles.pdfBytes());
    }

    @Test
    void createReport_keepsNoTraceOfTheNameChosenByTheUser() {
        var report = reportService.createReport(doctor.getUser(), completed.getId(), "Nessun risultato.", TestFiles.pdf());

        assertThat(report.getAttachmentStoredName()).doesNotContain("referto").doesNotContain("pdf").hasSize(36);
        assertThat(reportService.downloadOf(report).filename()).isEqualTo("referto-" + report.getId() + ".pdf");
    }

    @Test
    void createReport_recognizesJpegAndPngFromTheirBytes() {
        var jpeg = reportService.createReport(doctor.getUser(), completed.getId(), "Scansione.", TestFiles.jpeg());
        var other = completedVisit(patient, doctor, 2);
        var png = reportService.createReport(doctor.getUser(), other.getId(), "Scansione.", TestFiles.png());

        assertThat(jpeg.getAttachmentContentType()).isEqualTo("image/jpeg");
        assertThat(reportService.downloadOf(jpeg).filename()).endsWith(".jpg");
        assertThat(png.getAttachmentContentType()).isEqualTo("image/png");
        assertThat(reportService.downloadOf(png).filename()).endsWith(".png");
    }

    @Test
    void createReport_withoutAFile_hasNoAttachment() {
        var report = reportService.createReport(doctor.getUser(), completed.getId(), "Nessun risultato.", null);

        assertThat(report.hasAttachment()).isFalse();
        assertThat(report.getAttachmentContentType()).isNull();
        assertThat(report.getAttachmentSize()).isNull();
    }

    @Test
    void createReport_refusesAFileThatIsNotWhatItSays() {
        assertThrows(IllegalArgumentException.class,
                () -> reportService.createReport(doctor.getUser(), completed.getId(), "Nessun risultato.", TestFiles.fakePdf()));

        assertThat(reportRepository.findByAppointmentId(completed.getId())).isEmpty();
        assertThat(filesOnTheDisk()).isEqualTo(filesBefore);
    }

    @Test
    void createReport_refusesAnEmptyFile() {
        assertThrows(IllegalArgumentException.class,
                () -> reportService.createReport(doctor.getUser(), completed.getId(), "Nessun risultato.", TestFiles.empty()));
    }

    @Test
    void createReport_refusesAFileOverTenMegabytes() {
        assertThrows(FileTooLargeException.class,
                () -> reportService.createReport(doctor.getUser(), completed.getId(), "Nessun risultato.", TestFiles.tooLargePdf()));

        assertThat(filesOnTheDisk()).isEqualTo(filesBefore);
    }

    @Test
    void createReport_removesTheFile_whenTheReportCannotBeSaved() {
        // No price in the price list: the invoice cannot be issued, so the whole report fails after the file was written
        priceListRepository.deleteAll();

        assertThrows(RuntimeException.class,
                () -> reportService.createReport(doctor.getUser(), completed.getId(), "x".repeat(5001), TestFiles.pdf()));
    }

    @Test
    void createReport_removesTheFile_whenTheTransactionIsRolledBack() {
        reportService.createReport(doctor.getUser(), completed.getId(), "Nessun risultato.", TestFiles.pdf());
        assertThat(filesOnTheDisk()).isEqualTo(filesBefore + 1);

        // The failure happens after the method (for example at the commit): nobody catches it inside the service
        TestTransaction.flagForRollback();
        TestTransaction.end();

        assertThat(filesOnTheDisk()).isEqualTo(filesBefore);
    }
    @Test
    void createReport_doesNotLookAtTheFile_whenTheDoctorIsNotAllowed() {
        var colleague = createDoctor("att.service.colleague@test.it");

        assertThrows(UnauthorizedAccessException.class,
                () -> reportService.createReport(colleague.getUser(), completed.getId(), "Nessun risultato.", TestFiles.pdf()));

        assertThat(filesOnTheDisk()).isEqualTo(filesBefore);
    }

    @Test
    void createReport_doesNotLookAtTheFile_whenTheVisitIsNotCompleted() {
        var stillBooked = bookedVisit(patient, doctor, 3);

        assertThrows(InvalidStateTransitionException.class,
                () -> reportService.createReport(doctor.getUser(), stillBooked.getId(), "Nessun risultato.", TestFiles.pdf()));

        assertThat(filesOnTheDisk()).isEqualTo(filesBefore);
    }

    // ---- download -------------------------------------------------------------------------------

    @Test
    void getAttachment_returnsTheFile_toThePatientOwnerAndToTheAssignedDoctor() throws IOException {
        var report = reportService.createReport(doctor.getUser(), completed.getId(), "Nessun risultato.", TestFiles.pdf());

        var forPatient = reportService.getAttachment(patient.getUser(), report.getId());
        var forDoctor = reportService.getAttachment(doctor.getUser(), report.getId());

        assertThat(forPatient.contentType()).isEqualTo("application/pdf");
        assertThat(forPatient.sizeBytes()).isEqualTo(TestFiles.pdfBytes().length);
        assertThat(forPatient.resource().getInputStream().readAllBytes()).isEqualTo(TestFiles.pdfBytes());
        assertThat(forDoctor.filename()).isEqualTo(forPatient.filename());
    }

    @Test
    void getAttachment_throwsUnauthorizedAccessException_toAnotherPatientAndToAnAdmin() {
        var report = reportService.createReport(doctor.getUser(), completed.getId(), "Nessun risultato.", TestFiles.pdf());
        var other = createPatient("att.service.other@test.it");
        var admin = userRepository.save(UserEntity.builder().email("att.service.admin@test.it").passwordHash("hash").role(Role.ADMIN).build());

        assertThrows(UnauthorizedAccessException.class, () -> reportService.getAttachment(other.getUser(), report.getId()));
        assertThrows(UnauthorizedAccessException.class, () -> reportService.getAttachment(admin, report.getId()));
    }

    @Test
    void getAttachment_throwsResourceNotFoundException_whenTheReportHasNoAttachment() {
        var report = reportService.createReport(doctor.getUser(), completed.getId(), "Nessun risultato.", null);

        assertThrows(ResourceNotFoundException.class, () -> reportService.getAttachment(patient.getUser(), report.getId()));
    }

    @Test
    void getAttachment_throwsResourceNotFoundException_whenTheReportDoesNotExist() {
        assertThrows(ResourceNotFoundException.class, () -> reportService.getAttachment(patient.getUser(), 999_999L));
    }

    @Test
    void getAttachment_leavesATraceInTheAccessLog_evenWhenItIsRefused() {
        var report = reportService.createReport(doctor.getUser(), completed.getId(), "Nessun risultato.", TestFiles.pdf());
        var other = createPatient("att.service.other2@test.it");

        reportService.getAttachment(patient.getUser(), report.getId());
        assertThrows(UnauthorizedAccessException.class, () -> reportService.getAttachment(other.getUser(), report.getId()));

        var lines = auditLogRepository.findAll().stream().filter(line -> line.getAction().equals("READ_ATTACHMENT")).toList();
        assertThat(lines).extracting(line -> line.getOutcome()).containsExactlyInAnyOrder(AuditOutcome.SUCCESS, AuditOutcome.FAILURE);
        assertThat(lines).allSatisfy(line -> assertThat(line.getEntityId()).isEqualTo(report.getId()));
    }

    // ---- helpers --------------------------------------------------------------------------------

    private long filesOnTheDisk() {
        try (var files = Files.list(Path.of(storageDir))) {
            return files.count();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private AppointmentEntity bookedVisit(PatientEntity forPatient, DoctorEntity withDoctor, int daysFromNow) {
        var slot = slotRepository.save(AvailabilitySlotEntity.builder()
                .doctor(withDoctor).startTime(LocalDateTime.now().plusDays(daysFromNow)).durationMinutes(30).booked(false).build());
        return appointmentService.createAppointment(forPatient.getId(), slot.getId());
    }

    private AppointmentEntity completedVisit(PatientEntity forPatient, DoctorEntity withDoctor, int daysFromNow) {
        var appointment = bookedVisit(forPatient, withDoctor, daysFromNow);
        var done = appointmentService.completeVisit(appointment.getId(), withDoctor.getId());
        billingService.issueInvoiceItem(done);
        return done;
    }

    private DoctorEntity createDoctor(String email) {
        var user = userRepository.save(UserEntity.builder().email(email).passwordHash("hash").role(Role.DOCTOR).build());
        return doctorRepository.save(DoctorEntity.builder().user(user).firstName("Giovanni").lastName("Latorre").specialization(specialization).build());
    }

    private PatientEntity createPatient(String email) {
        var user = userRepository.save(UserEntity.builder().email(email).passwordHash("hash").role(Role.PATIENT).build());
        return patientRepository.save(PatientEntity.builder().user(user).firstName("Chiara").lastName("Aiello")
                .gdprConsent(true).gdprConsentTimestamp(Instant.now()).gdprConsentVersion("v1.0").anonymized(false).build());
    }
}