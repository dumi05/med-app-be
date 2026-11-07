package com.dc.medapp.medappbe.config;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.appointments.CancellationReason;
import com.dc.medapp.medappbe.appointments.NoShowRiskCalculator;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.InvoiceItemEntity;
import com.dc.medapp.medappbe.billing.InvoiceItemRepository;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.delegations.DelegationStatus;
import com.dc.medapp.medappbe.delegations.DoctorDelegationEntity;
import com.dc.medapp.medappbe.delegations.DoctorDelegationRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.news.NewsEntity;
import com.dc.medapp.medappbe.news.NewsRepository;
import com.dc.medapp.medappbe.patient.PatientEntity;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.reports.MedicalReportEntity;
import com.dc.medapp.medappbe.reports.MedicalReportRepository;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.schedule.SlotGenerator;
import com.dc.medapp.medappbe.schedule.WorkingHoursEntity;
import com.dc.medapp.medappbe.schedule.WorkingHoursRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demo data to try the features by hand (no-show risk, absence of a doctor, payment and invoice, reports...).
 * It runs only with {@code app.demo-data=true} (environment variable {@code DEMO_DATA}), after the {@link DataSeeder}.
 * The description of the data is in {@code med-app-infra/docs/demo-data.md}.
 * <p>
 * The past is written straight into the repositories (the booking refuses a slot that has already begun), the future
 * uses the slots that the working hours generate. Every date is relative to today, so the data stay useful whenever the
 * system is started. The password of all the demo accounts comes from {@code DEMO_PASSWORD}: it is never in the code.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements CommandLineRunner {

    public static final String ADMIN_EMAIL = "demo.admin@studio-medico.it";
    private static final String DOMAIN = "@demo.it";
    private static final Set<DayOfWeek> WORKING_DAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    private static final Set<DayOfWeek> MON_WED_FRI = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    private static final List<String> DIAGNOSES = List.of(
            "Esame obiettivo nella norma. Si consiglia un controllo fra sei mesi.",
            "Lieve rialzo dei valori di riferimento. Ripetere gli esami del sangue fra un mese.",
            "Nessun rilievo patologico. Proseguire la terapia in corso.",
            "Quadro stabile rispetto alla visita precedente. Si consiglia attività fisica moderata.");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SpecializationRepository specializationRepository;
    private final PriceListRepository priceListRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final SlotGenerator slotGenerator;
    private final AvailabilitySlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final MedicalReportRepository medicalReportRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final NewsRepository newsRepository;
    private final DoctorDelegationRepository delegationRepository;
    private final NoShowRiskCalculator noShowRiskCalculator;

    @Value("${app.demo-password:}")
    private String demoPassword;

    private final Map<Long, Integer> pastVisits = new HashMap<>();
    private final ZoneId zone = ZoneId.systemDefault();
    private int diagnosisCounter = 0;

    @Override
    @Transactional
    public void run(String... args) {
        if (demoPassword == null || demoPassword.isBlank()) {
            throw new IllegalStateException("I dati demo sono attivi (DEMO_DATA=true) ma manca la password: imposta DEMO_PASSWORD.");
        }
        if (userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).isPresent()) {
            log.info("Demo data already present: nothing to do.");
            return;
        }

        var now = LocalDateTime.now();
        var today = now.toLocalDate();

        createUser(ADMIN_EMAIL, Role.ADMIN);

        var cardiology = specialization("Cardiologia");
        var dermatology = specialization("Dermatologia");
        var general = specialization("Medicina Generale");

        // ---- doctors and their working hours ---------------------------------------------------
        var ferri = doctor("elena.ferri", "Elena", "Ferri", cardiology, false);
        hours(ferri, WORKING_DAYS, LocalTime.of(9, 0), LocalTime.of(13, 0));
        hours(ferri, WORKING_DAYS, LocalTime.of(14, 0), LocalTime.of(17, 0));
        var gallo = doctor("marco.gallo", "Marco", "Gallo", cardiology, false);
        hours(gallo, WORKING_DAYS, LocalTime.of(9, 0), LocalTime.of(12, 0));
        var neri = doctor("sara.neri", "Sara", "Neri", dermatology, false);
        hours(neri, MON_WED_FRI, LocalTime.of(9, 0), LocalTime.of(13, 0));
        var riva = doctor("paolo.riva", "Paolo", "Riva", general, true);
        hours(riva, WORKING_DAYS, LocalTime.of(8, 30), LocalTime.of(12, 30));
        var bruno = doctor("anna.bruno", "Anna", "Bruno", general, true);
        hours(bruno, WORKING_DAYS, LocalTime.of(14, 0), LocalTime.of(18, 0));
        List.of(ferri, gallo, neri, riva, bruno).forEach(doc -> slotGenerator.generate(doc, now));

        // ---- patients ---------------------------------------------------------------------------
        var rossi = patient("giulia.rossi", "Giulia", "Rossi");
        var bianchi = patient("luca.bianchi", "Luca", "Bianchi");
        var conti = patient("marta.conti", "Marta", "Conti");
        var greco = patient("davide.greco", "Davide", "Greco");
        patient("chiara.romano", "Chiara", "Romano");        // new patient: no history, no risk
        var moretti = patient("anna.moretti", "Anna", "Moretti");
        var villa = patient("franco.villa", "Franco", "Villa");
        var marini = patient("elisa.marini", "Elisa", "Marini");
        var costa = patient("giorgio.costa", "Giorgio", "Costa");
        var fontana = patient("laura.fontana", "Laura", "Fontana");
        var esposito = patient("matteo.esposito", "Matteo", "Esposito");

        // ---- the past: the history that the no-show risk is computed from ------------------------
        // Rossi: reliable (nothing missed). Bianchi: 3 of 4 missed. Conti: 1 of 2 (the limit). Greco: 1 of 3 (the cancelled ones do not count).
        past(rossi, riva, AppointmentStatus.REPORTED);
        past(rossi, riva, AppointmentStatus.REPORTED);
        past(rossi, riva, AppointmentStatus.REPORTED);
        past(rossi, riva, AppointmentStatus.INVOICED);
        past(bianchi, riva, AppointmentStatus.BOOKED);
        past(bianchi, riva, AppointmentStatus.BOOKED);
        past(bianchi, riva, AppointmentStatus.BOOKED);
        past(bianchi, riva, AppointmentStatus.COMPLETED);
        past(conti, ferri, AppointmentStatus.BOOKED);
        past(conti, ferri, AppointmentStatus.REPORTED);
        past(greco, riva, AppointmentStatus.BOOKED);
        past(greco, riva, AppointmentStatus.REPORTED);
        past(greco, riva, AppointmentStatus.COMPLETED);
        past(greco, riva, AppointmentStatus.CANCELLED);
        past(greco, riva, AppointmentStatus.CANCELLED);
        // the others: visits in every state, to try the payment, the invoice and the report
        past(moretti, ferri, AppointmentStatus.REPORTED);
        past(moretti, ferri, AppointmentStatus.INVOICED);
        past(moretti, ferri, AppointmentStatus.COMPLETED);
        past(villa, riva, AppointmentStatus.INVOICED);
        past(villa, riva, AppointmentStatus.COMPLETED);
        past(marini, bruno, AppointmentStatus.REPORTED);
        past(marini, bruno, AppointmentStatus.COMPLETED);
        past(costa, neri, AppointmentStatus.REPORTED);
        past(costa, neri, AppointmentStatus.CANCELLED);
        past(fontana, gallo, AppointmentStatus.REPORTED);
        past(esposito, gallo, AppointmentStatus.REPORTED);

        // ---- the future: the visits that an absence of the doctor will move or cancel ----------------
        var x = weekdayOnOrAfter(today.plusDays(8));
        var x2 = weekdayOnOrAfter(x.plusDays(1));
        var outside = weekdayOnOrAfter(x2.plusDays(8));
        var y = dayOnOrAfter(today.plusDays(8), MON_WED_FRI);

        // Ferri: Gallo (the colleague) is free at 9:30 and 11:00 and 9:00 of the next day -> moved; busy at 10:00, absent at 15:00 -> cancelled
        book(moretti, ferri, x.atTime(9, 30));
        book(costa, ferri, x.atTime(10, 0));
        book(villa, ferri, x.atTime(11, 0));
        book(marini, ferri, x.atTime(15, 0));
        book(esposito, ferri, x2.atTime(9, 0));
        book(fontana, gallo, x.atTime(10, 0));                 // this is why the 10:00 of Ferri cannot be moved
        book(moretti, ferri, outside.atTime(9, 0));            // out of the absence: it does not change
        book(conti, ferri, outside.atTime(10, 0));             // a patient at risk, out of the absence
        // Neri is the only one of her specialization: with an absence, all cancelled
        book(costa, neri, y.atTime(9, 0));
        book(fontana, neri, y.atTime(10, 30));
        // the doctors of general medicine: the first visits of a patient at risk and of a reliable one
        var soon = weekdayOnOrAfter(today.plusDays(2));
        book(bianchi, riva, soon.atTime(9, 0));
        book(rossi, riva, soon.atTime(9, 30));
        book(esposito, bruno, weekdayOnOrAfter(today.plusDays(3)).atTime(14, 30));

        // ---- the delegation between doctors, the notices of the studio ---------------------------------
        delegationRepository.save(DoctorDelegationEntity.builder()
                .delegatingDoctor(riva).delegateDoctor(bruno)
                .startDate(today).endDate(today.plusDays(7)).status(DelegationStatus.ACCEPTED).build());
        newsRepository.save(NewsEntity.builder().author(riva)
                .title("Studio chiuso il giorno dell'Immacolata")
                .body("Lo studio resterà chiuso l'8 dicembre. Le visite già prenotate non cambiano.")
                .expiresAt(today.plusDays(10)).build());
        newsRepository.save(NewsEntity.builder().author(riva)
                .title("Nuovi orari estivi (scaduto)")
                .body("Questo avviso è scaduto e non deve comparire a nessuno.")
                .expiresAt(today.minusDays(3)).build());

        log.info("Demo data created: 1 admin, 5 doctors, 11 patients. The password of every account is DEMO_PASSWORD.");
        log.info("Demo: try an absence of Dr. Ferri from {} to {} with Dr. Gallo as the replacement (3 visits moved, 2 cancelled).", x, x2);
        log.info("Demo: try an absence of Dr. Neri on {} with no replacement (2 visits cancelled).", y);
    }

    // ---- accounts -----------------------------------------------------------------------------------

    private UserEntity createUser(String email, Role role) {
        return userRepository.save(UserEntity.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(demoPassword))
                .role(role)
                .mustChangePassword(false)
                .build());
    }

    private SpecializationEntity specialization(String name) {
        return specializationRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Specializzazione non trovata: " + name + " (parte prima il DataSeeder?)"));
    }

    private DoctorEntity doctor(String emailName, String firstName, String lastName, SpecializationEntity specialization, boolean delegationConsent) {
        var user = createUser(emailName + DOMAIN, Role.DOCTOR);
        return doctorRepository.save(DoctorEntity.builder()
                .user(user).firstName(firstName).lastName(lastName).specialization(specialization).delegationConsent(delegationConsent).build());
    }

    private PatientEntity patient(String emailName, String firstName, String lastName) {
        var user = createUser(emailName + DOMAIN, Role.PATIENT);
        return patientRepository.save(PatientEntity.builder()
                .user(user).firstName(firstName).lastName(lastName).birthDate(LocalDate.of(1980, 5, 12))
                .gdprConsent(true).gdprConsentTimestamp(Instant.now()).gdprConsentVersion("v1.0").build());
    }

    private void hours(DoctorEntity doctor, Set<DayOfWeek> days, LocalTime start, LocalTime end) {
        days.forEach(day -> workingHoursRepository.save(
                WorkingHoursEntity.builder().doctor(doctor).dayOfWeek(day).startTime(start).endTime(end).build()));
    }

    // ---- the future: a booking on a slot that the working hours generated ----------------------------------

    private void book(PatientEntity patient, DoctorEntity doctor, LocalDateTime start) {
        var slot = slotRepository.findByDoctorIdAndStartTimeBetweenOrderByStartTime(doctor.getId(), start, start).stream()
                .filter(s -> s.getStartTime().equals(start) && !s.isBooked())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Nessuno slot libero per " + doctor.getLastName() + " alle " + start));
        slot.setBooked(true);
        appointmentRepository.save(AppointmentEntity.builder()
                .patient(patient).slot(slot).status(AppointmentStatus.BOOKED)
                .noShowRisk(noShowRiskCalculator.calculateRisk(patient.getId()))
                .bookedByRole(Role.PATIENT).build());
    }

    // ---- the past: a visit in the state that is asked ------------------------------------------------------

    private AppointmentEntity past(PatientEntity patient, DoctorEntity doctor, AppointmentStatus target) {
        var slot = pastSlot(doctor);
        var start = slot.getStartTime().atZone(zone).toInstant();
        var appointment = AppointmentEntity.builder()
                .patient(patient).slot(slot).status(AppointmentStatus.BOOKED).noShowRisk(false).bookedByRole(Role.PATIENT).build();

        if (target == AppointmentStatus.CANCELLED) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointment.setCancelledAt(start.minus(2, ChronoUnit.DAYS));
            appointment.setCancellationReason(CancellationReason.PATIENT);
            slot.setBooked(false);
            return appointmentRepository.save(appointment);
        }
        if (target == AppointmentStatus.BOOKED) {                 // never done: it stays booked in the past
            return appointmentRepository.save(appointment);
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setCompletedAt(start.plus(40, ChronoUnit.MINUTES));
        appointmentRepository.save(appointment);
        if (target == AppointmentStatus.COMPLETED) {
            return appointment;
        }

        var paidAt = start.plus(1, ChronoUnit.HOURS);
        var price = priceListRepository.findBySpecializationIdAndVisitType(doctor.getSpecialization().getId(), BillingService.STANDARD_VISIT_TYPE)
                .orElseThrow(() -> new IllegalStateException("Manca il prezzo per " + doctor.getSpecialization().getName()));
        var invoice = invoiceItemRepository.save(InvoiceItemEntity.builder()
                .appointment(appointment).amount(price.getPrice()).issuedAt(paidAt).paidAt(paidAt).build());
        invoice.setNumber(BillingService.numberOf(invoice));
        appointment.setStatus(AppointmentStatus.INVOICED);
        appointment.setInvoicedAt(paidAt);
        if (target == AppointmentStatus.INVOICED) {
            return appointment;
        }

        var reportedAt = start.plus(2, ChronoUnit.HOURS);
        medicalReportRepository.save(MedicalReportEntity.builder()
                .appointment(appointment).diagnosisText(DIAGNOSES.get(diagnosisCounter++ % DIAGNOSES.size())).completedAt(reportedAt).build());
        appointment.setStatus(AppointmentStatus.REPORTED);
        appointment.setReportedAt(reportedAt);
        return appointment;
    }

    /** A slot in the past, a different working day for every visit of the same doctor (every other day, going back). */
    private AvailabilitySlotEntity pastSlot(DoctorEntity doctor) {
        int counter = pastVisits.merge(doctor.getId(), 1, Integer::sum) - 1;
        var day = weekdaysBack().get(1 + 2 * counter);
        return slotRepository.save(AvailabilitySlotEntity.builder()
                .doctor(doctor).startTime(day.atTime(10, 0)).durationMinutes(30).booked(true).generated(false).build());
    }

    /** The working days before today, the nearest first. */
    private List<LocalDate> weekdaysBack() {
        var days = new ArrayList<LocalDate>();
        for (var day = LocalDate.now().minusDays(1); days.size() < 60; day = day.minusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(day);
            }
        }
        return days;
    }

    private static LocalDate weekdayOnOrAfter(LocalDate date) {
        return dayOnOrAfter(date, WORKING_DAYS);
    }

    private static LocalDate dayOnOrAfter(LocalDate date, Set<DayOfWeek> days) {
        var day = date;
        while (!days.contains(day.getDayOfWeek())) {
            day = day.plusDays(1);
        }
        return day;
    }
}
