package com.dc.medapp.medappbe.dashboard;

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
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import com.dc.medapp.medappbe.support.ApiTestClient;
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

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class DashboardServiceTest {

    @Autowired
    private DashboardService service;
    @Autowired
    private AppointmentRepository appointmentRepo;
    @Autowired
    private InvoiceItemRepository invoiceItemRepo;
    @Autowired
    private AvailabilitySlotRepository slotRepository;
    @Autowired
    private DoctorRepository doctorRepo;
    @Autowired
    private PatientRepository patientRepo;
    @Autowired
    private SpecializationRepository specRepo;
    @Autowired
    private UserRepository userRepo;

    private SpecializationEntity specialization;
    private DoctorEntity doctor;
    private PatientEntity patient;
    private ApiTestClient api;

    @BeforeEach
    void setUp() {
        specialization = specRepo.save(SpecializationEntity.builder().name("Cardiologia-test").build());
        doctor = createDoctor("cardiologo@test.it", specialization);
        var patientUser = userRepo.save(UserEntity.builder().email("patient.c@ok.co").passwordHash("password142").role(Role.PATIENT).build());
        patient = patientRepo.save(PatientEntity.builder().user(patientUser).anonymized(false).gdprConsentVersion("v1.0").gdprConsentTimestamp(Instant.now()).gdprConsent(true).firstName("Luigi").lastName("Dalmasso").birthDate(LocalDate.of(1978, 1,5)).build());
    }

    @Test
    void getKpi_returnsZeroes_whenThereIsNoData(){
        var kpi = service.getKpi();

        assertThat(kpi.totalAppointments()).isZero();
        assertThat(kpi.appointmentsByStatus()).containsOnlyKeys(AppointmentStatus.values());
        assertThat(kpi.appointmentsByStatus().values()).containsOnly(0L);
        assertThat(kpi.attendanceRate()).isZero();
        assertThat(kpi.totalRevenue()).isEqualByComparingTo("0");
        assertThat(kpi.revenueBySpecialization()).isEmpty();
    }

    @Test
    void getKpi_countsAppointmentsByStatus() {
        saveAppointment(inDays(2), AppointmentStatus.BOOKED, false);
        saveAppointment(inDays(3), AppointmentStatus.BOOKED, false);
        saveAppointment(inDays(-2), AppointmentStatus.COMPLETED, false);
        saveAppointment(inDays(-3), AppointmentStatus.CANCELLED, false);

        var kpi = service.getKpi();

        assertThat(kpi.totalAppointments()).isEqualTo(4);
        assertThat(kpi.appointmentsByStatus().get(AppointmentStatus.BOOKED)).isEqualTo(2);
        assertThat(kpi.appointmentsByStatus().get(AppointmentStatus.COMPLETED)).isEqualTo(1);
        assertThat(kpi.appointmentsByStatus().get(AppointmentStatus.CANCELLED)).isEqualTo(1);
        assertThat(kpi.appointmentsByStatus().get(AppointmentStatus.REPORTED)).isZero();
    }

    @Test
    void getKpi_countsOnlyUpcomingBookedAppointmentsAtRisk() {
        saveAppointment(inDays(2), AppointmentStatus.BOOKED, true);
        saveAppointment(inDays(3), AppointmentStatus.BOOKED, false);
        saveAppointment(inDays(-2), AppointmentStatus.BOOKED, true);
        saveAppointment(inDays(4), AppointmentStatus.CANCELLED, true);

        assertThat(service.getKpi().upcomingAtRisk()).isEqualTo(1);
    }

    @Test
    void getKpi_attendanceRate_isHeldVisitsOverHeldPlusMissed() {
        saveAppointment(inDays(-5), AppointmentStatus.COMPLETED, false);
        saveAppointment(inDays(-4), AppointmentStatus.REPORTED, false);
        saveAppointment(inDays(-3), AppointmentStatus.INVOICED, false);
        saveAppointment(inDays(-2), AppointmentStatus.BOOKED, false);
        saveAppointment(inDays(2), AppointmentStatus.BOOKED, false);
        saveAppointment(inDays(-6), AppointmentStatus.CANCELLED, false);

        assertThat(service.getKpi().attendanceRate()).isEqualTo(0.75);
    }

    @Test
    void getKpi_sumsRevenue_overallAndBySpecialization() {
        var dermatology = specRepo.save(SpecializationEntity.builder().name("Dermatologia-test").build());
        var dermatologist = createDoctor("dematologo@test.it", dermatology);

        saveInvoiceItem(saveAppointment(doctor, inDays(-3), AppointmentStatus.INVOICED, false), "100.00");
        saveInvoiceItem(saveAppointment(doctor, inDays(-2), AppointmentStatus.INVOICED, false), "50.00");
        saveInvoiceItem(saveAppointment(dermatologist, inDays(-1), AppointmentStatus.INVOICED, false), "80.00");

        var kpi = service.getKpi();

        assertThat(kpi.totalRevenue()).isEqualTo("230.00");
        assertThat(kpi.revenueBySpecialization()).hasSize(2);
        assertThat(kpi.revenueBySpecialization().get(0).specialization()).isEqualTo("Cardiologia-test");
        assertThat(kpi.revenueBySpecialization().get(0).revenue()).isEqualByComparingTo("150.00");
        assertThat(kpi.revenueBySpecialization().get(1).specialization()).isEqualTo("Dermatologia-test");
        assertThat(kpi.revenueBySpecialization().get(1).revenue()).isEqualByComparingTo("80.00");
    }

    private LocalDateTime inDays(int days){
        return LocalDateTime.now().plusDays(days);
    }

    private AppointmentEntity saveAppointment(LocalDateTime startTime, AppointmentStatus status, boolean noShowRisk) {
        return saveAppointment(doctor, startTime, status, noShowRisk);
    }

    private AppointmentEntity saveAppointment(DoctorEntity doc, LocalDateTime startTime, AppointmentStatus status, boolean noShowRisk) {
        var slot = slotRepository.save(AvailabilitySlotEntity.builder().startTime(startTime).doctor(doc).durationMinutes(30).booked(true).build());
        return appointmentRepo.save(AppointmentEntity.builder().patient(patient).slot(slot).status(status).noShowRisk(noShowRisk).build());
    }

    private void saveInvoiceItem(AppointmentEntity appointment, String amount){
        invoiceItemRepo.save(
                InvoiceItemEntity.builder()
                        .amount(new BigDecimal(amount))
                        .appointment(appointment)
                        .issuedAt(Instant.now())
                        .build()
        );
    }

    private DoctorEntity createDoctor(String mail, SpecializationEntity specialization) {
        var user = userRepo.save(UserEntity.builder().role(Role.DOCTOR).passwordHash("password234").email(mail).build());
        return doctorRepo.save(DoctorEntity.builder().user(user).specialization(specialization).delegationConsent(true).firstName("Rita").lastName("Levia").build());
    }


}
