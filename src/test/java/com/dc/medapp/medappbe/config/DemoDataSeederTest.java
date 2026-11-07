package com.dc.medapp.medappbe.config;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.appointments.NoShowRiskCalculator;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.InvoiceItemRepository;
import com.dc.medapp.medappbe.delegations.DoctorDelegationRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.leaves.LeaveService;
import com.dc.medapp.medappbe.leaves.dto.LeavePreviewResponse;
import com.dc.medapp.medappbe.news.NewsRepository;
import com.dc.medapp.medappbe.patient.PatientEntity;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.reports.MedicalReportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seeder runs when the application starts, so this test has its own database (the other tests share "testdb"
 * and a second context on the same one would drop its tables). Nothing here writes: it only reads what the seeder made.
 */
@SpringBootTest(properties = {
        "app.demo-data=true",
        "app.demo-password=Demo-Password-2026!",
        "spring.datasource.url=jdbc:h2:mem:demotestdb;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
public class DemoDataSeederTest {

    @Autowired private DemoDataSeeder seeder;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private InvoiceItemRepository invoiceItemRepository;
    @Autowired private MedicalReportRepository reportRepository;
    @Autowired private NewsRepository newsRepository;
    @Autowired private DoctorDelegationRepository delegationRepository;
    @Autowired private NoShowRiskCalculator noShowRiskCalculator;
    @Autowired private LeaveService leaveService;

    private DoctorEntity doctor(String lastName) {
        return doctorRepository.findAll().stream().filter(d -> d.getLastName().equals(lastName)).findFirst().orElseThrow();
    }

    private PatientEntity patient(String lastName) {
        return patientRepository.findAll().stream().filter(p -> p.getLastName().equals(lastName)).findFirst().orElseThrow();
    }

    private List<AppointmentEntity> futureBookings(DoctorEntity doctor) {
        return appointmentRepository.findBookedOfDoctorBetween(doctor.getId(), LocalDateTime.now(), LocalDateTime.now().plusDays(90));
    }

    @Test
    void itCreatesTheAccounts_withThePasswordOfTheConfiguration() {
        var admin = userRepository.findByEmailIgnoreCase(DemoDataSeeder.ADMIN_EMAIL).orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isMustChangePassword()).isFalse();
        assertThat(passwordEncoder.matches("Demo-Password-2026!", admin.getPasswordHash())).isTrue();
        assertThat(doctorRepository.findAll().stream().filter(d -> d.getUser().getEmail().endsWith("@demo.it")).count()).isEqualTo(5);
        assertThat(patientRepository.findAll().stream().filter(p -> p.getUser().getEmail().endsWith("@demo.it")).count()).isEqualTo(11);
    }

    @Test
    void thePatientsWithMissedVisits_areAtRisk_andTheOthersAreNot() {
        assertThat(noShowRiskCalculator.calculateRisk(patient("Bianchi").getId())).isTrue();   // 3 of 4 missed
        assertThat(noShowRiskCalculator.calculateRisk(patient("Conti").getId())).isTrue();     // 1 of 2: the limit counts
        assertThat(noShowRiskCalculator.calculateRisk(patient("Rossi").getId())).isFalse();    // nothing missed
        assertThat(noShowRiskCalculator.calculateRisk(patient("Greco").getId())).isFalse();    // 1 of 3, and the cancelled ones do not count
        assertThat(noShowRiskCalculator.calculateRisk(patient("Romano").getId())).isFalse();   // no history
    }

    @Test
    void theFutureVisitsKeepTheRiskOfTheirPatient() {
        var riva = futureBookings(doctor("Riva"));

        assertThat(riva).filteredOn(a -> a.getPatient().getLastName().equals("Bianchi")).singleElement().satisfies(a -> assertThat(a.isNoShowRisk()).isTrue());
        assertThat(riva).filteredOn(a -> a.getPatient().getLastName().equals("Rossi")).singleElement().satisfies(a -> assertThat(a.isNoShowRisk()).isFalse());
        assertThat(futureBookings(doctor("Ferri"))).filteredOn(a -> a.getPatient().getLastName().equals("Conti"))
                .singleElement().satisfies(a -> assertThat(a.isNoShowRisk()).isTrue());
    }

    @Test
    void anAbsenceOfFerri_withGalloAsReplacement_movesThree_andCancelsTwo() {
        var ferri = doctor("Ferri");
        var days = futureBookings(ferri).stream().map(a -> a.getSlot().getStartTime().toLocalDate()).distinct().sorted().toList();
        var start = days.get(0);
        var end = days.get(1);

        var preview = leaveService.preview(ferri.getUser(), ferri.getId(), start, end, doctor("Gallo").getId());

        assertThat(preview.reassign()).isEqualTo(3);
        assertThat(preview.cancel()).isEqualTo(2);
        assertThat(preview.appointments()).extracting(LeavePreviewResponse.AffectedAppointment::outcome)
                .containsExactlyInAnyOrder(LeavePreviewResponse.Outcome.REASSIGN, LeavePreviewResponse.Outcome.REASSIGN, LeavePreviewResponse.Outcome.REASSIGN,
                        LeavePreviewResponse.Outcome.CANCEL, LeavePreviewResponse.Outcome.CANCEL);
        // the visit of the third week is out of the absence
        assertThat(days).hasSizeGreaterThan(2);
    }

    @Test
    void anAbsenceOfNeri_withNoReplacement_cancelsEverything() {
        var neri = doctor("Neri");
        var day = futureBookings(neri).get(0).getSlot().getStartTime().toLocalDate();

        var preview = leaveService.preview(neri.getUser(), neri.getId(), day, day, null);

        assertThat(preview.reassign()).isZero();
        assertThat(preview.cancel()).isEqualTo(2);
    }

    @Test
    void thePastHasVisitsInEveryState_withInvoicesAndReports() {
        var all = appointmentRepository.findAll();
        var now = LocalDateTime.now();

        assertThat(all).filteredOn(a -> a.getStatus() == AppointmentStatus.COMPLETED).isNotEmpty();
        assertThat(all).filteredOn(a -> a.getStatus() == AppointmentStatus.INVOICED).isNotEmpty();
        assertThat(all).filteredOn(a -> a.getStatus() == AppointmentStatus.REPORTED).isNotEmpty();
        assertThat(all).filteredOn(a -> a.getStatus() == AppointmentStatus.CANCELLED).isNotEmpty();
        assertThat(all).filteredOn(a -> a.getStatus() == AppointmentStatus.BOOKED && a.getSlot().getStartTime().isBefore(now)).isNotEmpty();

        for (var invoice : invoiceItemRepository.findAll()) {
            assertThat(invoice.getNumber()).matches("FT-\\d{4}-\\d{5}");
            assertThat(invoice.getPaidAt()).isEqualTo(invoice.getIssuedAt());
            assertThat(invoice.getAppointment().getStatus()).isIn(AppointmentStatus.INVOICED, AppointmentStatus.REPORTED);
        }
        var reported = all.stream().filter(a -> a.getStatus() == AppointmentStatus.REPORTED).toList();
        assertThat(reportRepository.findAll()).hasSize(reported.size());
        assertThat(invoiceItemRepository.count()).isEqualTo(all.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.INVOICED || a.getStatus() == AppointmentStatus.REPORTED).count());
    }

    @Test
    void theDelegationBetweenDoctorsIsActive_andTheExpiredNoticeIsNotShown() {
        assertThat(delegationRepository.existsActiveDelegation(doctor("Riva").getId(), doctor("Bruno").getId(), LocalDate.now())).isTrue();
        assertThat(delegationRepository.existsActiveDelegation(doctor("Bruno").getId(), doctor("Riva").getId(), LocalDate.now())).isFalse();

        var active = newsRepository.findByExpiresAtGreaterThanEqualOrderByCreatedAtDescIdDesc(LocalDate.now());
        assertThat(active).extracting(n -> n.getTitle()).contains("Studio chiuso il giorno dell'Immacolata").doesNotContain("Nuovi orari estivi (scaduto)");
    }

    @Test
    void runningTheSeederAgain_createsNothing() throws Exception {
        long users = userRepository.count();
        long appointments = appointmentRepository.count();

        seeder.run();

        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(appointmentRepository.count()).isEqualTo(appointments);
    }
}
