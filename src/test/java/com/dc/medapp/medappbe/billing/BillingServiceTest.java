package com.dc.medapp.medappbe.billing;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentService;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class BillingServiceTest {

    @Autowired
    private BillingService billingService;
    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private PriceListRepository priceListRepository;
    @Autowired
    private InvoiceItemRepository invoiceItemRepository;
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

    private SpecializationEntity specialization;
    private AppointmentEntity completedAppointment;
    private PriceListEntity price;

    @BeforeEach
    void setUpd() {
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-billing-test").build());
        price = priceListRepository.save(PriceListEntity.builder().specialization(specialization).visitType(BillingService.STANDARD_VISIT_TYPE).price(new BigDecimal("100.00")).build());

        var doctor = createDoctor("billing.doc@test.it");
        var patient = createPatient("billing.pat@test.it");
        var slot = slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).startTime(LocalDateTime.now().plusDays(1)).durationMinutes(30).booked(false).build());

        var booked = appointmentService.createAppointment(patient.getId(), slot.getId());
        completedAppointment = appointmentService.completeVisit(booked.getId(),doctor.getId());
    }

    @Test
    void issueInvoiceItem_storesTheListPriceAndMarksAppointmentAsInvoiced() {
        var item = billingService.issueInvoiceItem(completedAppointment);

        assertThat(item.getId()).isNotNull();
        assertThat(item.getAmount()).isEqualByComparingTo("100.00");
        assertThat(item.getIssuedAt()).isNotNull();
        assertThat(item.getPaidAt()).isEqualTo(item.getIssuedAt());
        assertThat(item.getNumber()).isEqualTo("FT-%d-%05d", LocalDate.now().getYear(), item.getId());
        assertThat(completedAppointment.getStatus()).isEqualTo(AppointmentStatus.INVOICED);
        assertThat(completedAppointment.getInvoicedAt()).isNotNull();
        assertThat(invoiceItemRepository.findByAppointmentId(completedAppointment.getId())).isPresent();
    }

    @Test
    void issueInvoiceItem_keepsTheIssuedAmount_whenThePriceListChangesLater() {
        var item = billingService.issueInvoiceItem(completedAppointment);

        price.setPrice(new BigDecimal("150.00"));
        priceListRepository.save(price);

        assertThat(invoiceItemRepository.findById(item.getId()).orElseThrow().getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void issueInvoiceItem_throwsInvalidStateTransitionException_whenAlreadyInvoiced() {
        billingService.issueInvoiceItem(completedAppointment);

        assertThrows(InvalidStateTransitionException.class, () -> billingService.issueInvoiceItem(completedAppointment));
    }

    @Test
    void issueInvoicedItem_throwsInvalidStateTransitionException_forABookedVisit(){
        completedAppointment.setStatus(AppointmentStatus.BOOKED);

        assertThrows(InvalidStateTransitionException.class, () -> billingService.issueInvoiceItem(completedAppointment));
        assertThat(invoiceItemRepository.findByAppointmentId(completedAppointment.getId())).isEmpty();
    }

    @Test
    void issueInvoicedItem_keepsTheIssuedAmount_whenThePriceListChangesLater(){
        var item = billingService.issueInvoiceItem(completedAppointment);

        price.setPrice(new BigDecimal("150.00"));
        priceListRepository.save(price);

        assertThat(invoiceItemRepository.findByAppointmentId(completedAppointment.getId()).orElseThrow().getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void issueInvoicedItem_throwsInvalidStateTransitionException_whenAppointmentIsNotCompleted(){
        completedAppointment.setStatus(AppointmentStatus.REPORTED);

        assertThrows(InvalidStateTransitionException.class, () -> billingService.issueInvoiceItem(completedAppointment));
    }

    @Test
    void issueInvoiceItem_throwsResourceNotFoundException_whenThereIsNoPriceForTheSpecialization(){
        priceListRepository.deleteAll();

        assertThrows(ResourceNotFoundException.class, () -> billingService.issueInvoiceItem(completedAppointment));
    }

    private DoctorEntity createDoctor(String email) {
        var user = userRepository.save(UserEntity.builder().email(email).role(Role.DOCTOR).passwordHash("password").createdAt(Instant.now()).build());
        return doctorRepository.save(DoctorEntity.builder().user(user).specialization(specialization).firstName("Anotnio").lastName("Tonio").build());
    }

    private PatientEntity createPatient(String email) {
        var user = userRepository.save(UserEntity.builder().role(Role.PATIENT).email(email).passwordHash("password").createdAt(Instant.now()).build());
        return patientRepository.save(PatientEntity.builder().user(user).firstName("Giulia").lastName("Aro").gdprConsent(true).gdprConsentVersion("v1.0").gdprConsentTimestamp(Instant.now()).anonymized(false).build());
    }
}
