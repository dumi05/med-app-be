package com.dc.medapp.medappbe.config;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.InvoiceItemEntity;
import com.dc.medapp.medappbe.billing.InvoiceItemRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.patient.PatientEntity;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.reports.MedicalReportEntity;
import com.dc.medapp.medappbe.reports.MedicalReportRepository;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class InvoiceOrderMigrationTest {

    @Autowired private InvoiceOrderMigration migration;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private InvoiceItemRepository invoiceItemRepository;
    @Autowired private MedicalReportRepository reportRepository;
    @Autowired private AvailabilitySlotRepository slotRepository;
    @Autowired private SpecializationRepository specializationRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UserRepository userRepository;

    private DoctorEntity doctor;
    private PatientEntity patient;
    private int hour;

    @BeforeEach
    void setUp() {
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-migration-test").build());
        var doctorUser = userRepository.save(UserEntity.builder().email("migration.doctor@test.it").passwordHash("x").role(Role.DOCTOR).build());
        doctor = doctorRepository.save(DoctorEntity.builder().user(doctorUser).firstName("Mario").lastName("Rossi").specialization(specialization).build());
        var patientUser = userRepository.save(UserEntity.builder().email("migration.patient@test.it").passwordHash("x").role(Role.PATIENT).build());
        patient = patientRepository.save(PatientEntity.builder().user(patientUser).firstName("Anna").lastName("Verdi")
                .gdprConsent(true).gdprConsentTimestamp(Instant.now()).gdprConsentVersion("v1.0").build());
    }

    private AppointmentEntity appointment(AppointmentStatus status) {
        var slot = slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).booked(true).durationMinutes(30)
                .startTime(LocalDateTime.now().minusDays(2).plusHours(hour++)).build());
        return appointmentRepository.save(AppointmentEntity.builder().slot(slot).patient(patient).status(status).noShowRisk(false)
                .invoicedAt(Instant.now().minusSeconds(3600)).build());
    }

    private void report(AppointmentEntity appointment) {
        reportRepository.save(MedicalReportEntity.builder().appointment(appointment).diagnosisText("Nessun risultato.").completedAt(Instant.now()).build());
    }

    private InvoiceItemEntity oldInvoice(AppointmentEntity appointment) {
        return invoiceItemRepository.save(InvoiceItemEntity.builder().appointment(appointment).amount(new BigDecimal("100.00"))
                .issuedAt(Instant.now().minusSeconds(3600)).build());
    }

    @Test
    void aVisitInvoicedAfterItsReport_withTheOldOrder_becomesReported() {
        var old = appointment(AppointmentStatus.INVOICED);
        report(old);

        migration.run();

        assertThat(appointmentRepository.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.REPORTED);
        assertThat(appointmentRepository.findById(old.getId()).orElseThrow().getReportedAt()).isNotNull();
    }

    @Test
    void aVisitInvoicedWithoutAReport_isTheNewMeaning_andStaysInvoiced() {
        var waiting = appointment(AppointmentStatus.INVOICED);

        migration.run();

        assertThat(appointmentRepository.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.INVOICED);
    }

    @Test
    void theOtherStatuses_areLeftAlone() {
        var completed = appointment(AppointmentStatus.COMPLETED);
        var reported = appointment(AppointmentStatus.REPORTED);
        report(reported);

        migration.run();

        assertThat(appointmentRepository.findById(completed.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(appointmentRepository.findById(reported.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.REPORTED);
    }

    @Test
    void anOldInvoice_getsItsNumberAndItsPaymentDate() {
        var old = oldInvoice(appointment(AppointmentStatus.REPORTED));

        migration.run();

        var migrated = invoiceItemRepository.findById(old.getId()).orElseThrow();
        assertThat(migrated.getNumber()).isEqualTo(String.format("FT-%d-%05d", java.time.LocalDate.now().getYear(), old.getId()));
        assertThat(migrated.getPaidAt()).isEqualTo(migrated.getIssuedAt());
    }

    @Test
    void aNumberAndADateThatAreAlreadyThere_areNotTouched() {
        var done = oldInvoice(appointment(AppointmentStatus.REPORTED));
        done.setNumber("FT-2026-99999");
        done.setPaidAt(done.getIssuedAt().plusSeconds(60));
        invoiceItemRepository.save(done);

        migration.run();

        var same = invoiceItemRepository.findById(done.getId()).orElseThrow();
        assertThat(same.getNumber()).isEqualTo("FT-2026-99999");
        assertThat(same.getPaidAt()).isEqualTo(done.getIssuedAt().plusSeconds(60));
    }

    @Test
    void itCanRunAgain_withoutChangingAnything() {
        var old = appointment(AppointmentStatus.INVOICED);
        report(old);
        var invoice = oldInvoice(old);
        migration.run();
        var numberAfterTheFirstRun = invoiceItemRepository.findById(invoice.getId()).orElseThrow().getNumber();

        migration.run();

        assertThat(invoiceItemRepository.findById(invoice.getId()).orElseThrow().getNumber()).isEqualTo(numberAfterTheFirstRun);
        assertThat(appointmentRepository.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.REPORTED);
    }
}