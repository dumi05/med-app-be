package com.dc.medapp.medappbe.billing;

import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.audit.AuditLogRepository;
import com.dc.medapp.medappbe.audit.AuditOutcome;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.delegations.DelegationStatus;
import com.dc.medapp.medappbe.delegations.DoctorDelegationEntity;
import com.dc.medapp.medappbe.delegations.DoctorDelegationRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.reports.dto.CreateMedicalReportRequest;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import com.dc.medapp.medappbe.support.ApiTestClient;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class InvoiceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private SpecializationRepository specializationRepository;
    @Autowired private PriceListRepository priceListRepository;
    @Autowired private AvailabilitySlotRepository slotRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private InvoiceItemRepository invoiceItemRepository;
    @Autowired private DoctorDelegationRepository delegationRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private ApiTestClient api;
    private SpecializationEntity specialization;
    private PriceListEntity price;
    private DoctorEntity doctor;
    private DoctorEntity colleague;
    private String doctorToken;
    private String colleagueToken;
    private String adminToken;
    private String patientToken;
    private long visit;                // done, not yet paid
    private int hour;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-invoice-test").build());
        price = priceListRepository.save(PriceListEntity.builder().specialization(specialization)
                .visitType(BillingService.STANDARD_VISIT_TYPE).price(new BigDecimal("120.50")).build());
        doctor = api.createDoctorWithConsentFlag("invoice.doctor@test.it", true, specialization);
        colleague = api.createDoctorWithConsentFlag("invoice.colleague@test.it", true, specialization);
        doctorToken = api.login("invoice.doctor@test.it");
        colleagueToken = api.login("invoice.colleague@test.it");
        api.createUser("invoice.admin@test.it", Role.ADMIN);
        adminToken = api.login("invoice.admin@test.it");
        patientToken = api.registerPatient("invoice.patient@test.it");
        visit = completedVisit(patientToken);
    }

    private long completedVisit(String forPatientToken) throws Exception {
        var slot = slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).booked(false).durationMinutes(30)
                .startTime(LocalDateTime.now().plusDays(2).plusHours(hour++)).build());
        var id = api.book(forPatientToken, slot.getId());
        api.postAs(doctorToken, "/api/v1/appointments/" + id + "/complete", null).andExpect(status().isOk());
        return id;
    }

    private String path(long appointmentId) {
        return "/api/v1/appointments/" + appointmentId + "/invoice";
    }

    // ---- paying: the invoice is issued ----------------------------------------------------------

    @Test
    void doctor_confirmsThePayment_andTheInvoiceIsIssued() throws Exception {
        api.postAs(doctorToken, path(visit), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentId").value(visit))
                .andExpect(jsonPath("$.number", matchesPattern("FT-\\d{4}-\\d{5}")))
                .andExpect(jsonPath("$.amount").value(120.5))
                .andExpect(jsonPath("$.issuedAt").isNotEmpty())
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        var appointment = appointmentRepository.findById(visit).orElseThrow();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.INVOICED);
        assertThat(appointment.getInvoicedAt()).isNotNull();
    }

    @Test
    void theNumber_hasTheYearAndTheIdOfTheInvoice_andIsUnique() throws Exception {
        api.postAs(doctorToken, path(visit), null).andExpect(status().isOk());
        var second = completedVisit(api.registerPatient("invoice.second@test.it"));
        api.postAs(doctorToken, path(second), null).andExpect(status().isOk());

        var first = invoiceItemRepository.findByAppointmentId(visit).orElseThrow();
        var other = invoiceItemRepository.findByAppointmentId(second).orElseThrow();
        assertThat(first.getNumber()).isEqualTo(String.format("FT-%d-%05d", LocalDate.now().getYear(), first.getId()));
        assertThat(first.getNumber()).isNotEqualTo(other.getNumber());
    }

    @Test
    void theAmount_isTheOneOfTheDay_andDoesNotChangeWithTheList() throws Exception {
        api.postAs(doctorToken, path(visit), null).andExpect(status().isOk());
        price.setPrice(new BigDecimal("999.00"));
        priceListRepository.save(price);

        api.getAs(patientToken, path(visit)).andExpect(jsonPath("$.amount").value(120.5));
    }

    @Test
    void aVisitThatIsNotCompleted_orAlreadyInvoiced_cannotBeInvoiced() throws Exception {
        var slot = slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).booked(false).durationMinutes(30)
                .startTime(LocalDateTime.now().plusDays(3)).build());
        var stillBooked = api.book(api.registerPatient("invoice.booked@test.it"), slot.getId());

        api.postAs(doctorToken, path(stillBooked), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        api.postAs(doctorToken, path(visit), null).andExpect(status().isOk());
        api.postAs(doctorToken, path(visit), null).andExpect(status().isConflict());
        assertThat(invoiceItemRepository.findByAppointmentId(stillBooked)).isEmpty();
    }

    @Test
    void withoutAPriceInTheList_theVisitCannotBeInvoiced_andStaysCompleted() throws Exception {
        priceListRepository.deleteAll();

        api.postAs(doctorToken, path(visit), null).andExpect(status().isNotFound());
        assertThat(appointmentRepository.findById(visit).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void onlyTheDoctorOfTheVisit_orADelegate_canInvoice() throws Exception {
        api.postAs(colleagueToken, path(visit), null).andExpect(status().isForbidden());
        api.postAs(patientToken, path(visit), null).andExpect(status().isForbidden());
        api.postAs(adminToken, path(visit), null).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path(visit))).andExpect(status().isUnauthorized());
        assertThat(invoiceItemRepository.findByAppointmentId(visit)).isEmpty();

        delegationRepository.save(DoctorDelegationEntity.builder().delegatingDoctor(doctor).delegateDoctor(colleague)
                .startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(5)).status(DelegationStatus.ACCEPTED).build());
        api.postAs(colleagueToken, path(visit), null).andExpect(status().isOk());
    }

    @Test
    void anUnknownVisit_receives404() throws Exception {
        api.postAs(doctorToken, path(999_999), null).andExpect(status().isNotFound());
    }

    // ---- the order: the report comes after the payment ------------------------------------------

    @Test
    void theReportCannotBeWrittenBeforeThePayment_butCanAfterIt() throws Exception {
        api.postReportWithoutInvoice(doctorToken, new CreateMedicalReportRequest(visit, "Nessun risultato."), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        api.postAs(doctorToken, path(visit), null).andExpect(status().isOk());
        api.postReportWithoutInvoice(doctorToken, new CreateMedicalReportRequest(visit, "Nessun risultato."), null).andExpect(status().isOk());

        assertThat(appointmentRepository.findById(visit).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.REPORTED);
    }

    @Test
    void theInvoice_staysReadable_afterTheReport() throws Exception {
        api.postAs(doctorToken, path(visit), null).andExpect(status().isOk());
        api.postReportWithoutInvoice(doctorToken, new CreateMedicalReportRequest(visit, "Nessun risultato."), null).andExpect(status().isOk());

        api.getAs(patientToken, path(visit)).andExpect(status().isOk()).andExpect(jsonPath("$.number").isNotEmpty());
    }

    @Test
    void theRevenue_countsFromThePayment_notFromTheReport() throws Exception {
        var before = objectMapper.readTree(api.getAs(adminToken, "/api/v1/dashboard/kpi").andReturn().getResponse().getContentAsString()).get("totalRevenue").asDouble();

        api.postAs(doctorToken, path(visit), null).andExpect(status().isOk());

        api.getAs(adminToken, "/api/v1/dashboard/kpi")
                .andExpect(jsonPath("$.totalRevenue").value(before + 120.5))
                .andExpect(jsonPath("$.appointmentsByStatus.INVOICED").isNumber());
    }

    // ---- reading the invoice --------------------------------------------------------------------

    @Test
    void thePatient_theDoctorAndTheAdmin_readTheInvoice() throws Exception {
        api.postAs(doctorToken, path(visit), null).andExpect(status().isOk());

        api.getAs(patientToken, path(visit)).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(120.5));
        api.getAs(doctorToken, path(visit)).andExpect(status().isOk());
        api.getAs(adminToken, path(visit)).andExpect(status().isOk());
    }

    @Test
    void anotherPatient_orAnotherDoctor_cannotReadIt_receives403() throws Exception {
        api.postAs(doctorToken, path(visit), null).andExpect(status().isOk());
        var otherPatient = api.registerPatient("invoice.intruder@test.it");

        api.getAs(otherPatient, path(visit)).andExpect(status().isForbidden());
        api.getAs(otherPatient, path(visit) + "/pdf").andExpect(status().isForbidden());
        api.getAs(colleagueToken, path(visit)).andExpect(status().isForbidden());
        mockMvc.perform(get(path(visit))).andExpect(status().isUnauthorized());
    }

    @Test
    void beforeThePayment_thereIsNoInvoice_receives404() throws Exception {
        api.getAs(patientToken, path(visit)).andExpect(status().isNotFound());
        api.getAs(patientToken, path(visit) + "/pdf").andExpect(status().isNotFound());
    }

    // ---- the PDF --------------------------------------------------------------------------------

    @Test
    void thePdf_isADownload_namedAfterTheNumber() throws Exception {
        api.postAs(doctorToken, path(visit), null).andExpect(status().isOk());
        var number = invoiceItemRepository.findByAppointmentId(visit).orElseThrow().getNumber();

        api.getAs(patientToken, path(visit) + "/pdf")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("fattura-" + number + ".pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void thePdf_saysWhatIsInTheInvoice_andThatItIsADemonstration() throws Exception {
        api.postAs(doctorToken, path(visit), null).andExpect(status().isOk());
        var number = invoiceItemRepository.findByAppointmentId(visit).orElseThrow().getNumber();

        var bytes = api.getAs(patientToken, path(visit) + "/pdf").andReturn().getResponse().getContentAsByteArray();
        var text = textOf(bytes);

        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        assertThat(text).contains("Studio medico La Mole")
                .contains("Fattura " + number)
                .contains("Documento dimostrativo senza valore fiscale")
                .contains("Luca Amauri")
                .contains("Dott. Gianpaolo Cortelesi")
                .contains("Cardiologia-invoice-test")
                .contains("120,50")
                .contains("Ricevuto il " + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(LocalDate.now()));
    }

    private static String textOf(byte[] pdf) throws IOException {
        var reader = new PdfReader(pdf);
        try {
            var extractor = new PdfTextExtractor(reader);
            var text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }

    // ---- the access log -------------------------------------------------------------------------

    @Test
    void issuingAndReadingTheInvoice_areInTheAccessLog_theRefusedReadToo() throws Exception {
        api.postAs(doctorToken, path(visit), null).andExpect(status().isOk());
        api.getAs(patientToken, path(visit)).andExpect(status().isOk());
        api.getAs(colleagueToken, path(visit)).andExpect(status().isForbidden());

        var lines = auditLogRepository.findAll();
        assertThat(lines).filteredOn(line -> "ISSUE_INVOICE".equals(line.getAction())).extracting("outcome").contains(AuditOutcome.SUCCESS);
        assertThat(lines).filteredOn(line -> "READ_INVOICE".equals(line.getAction()))
                .extracting("outcome").containsExactlyInAnyOrder(AuditOutcome.SUCCESS, AuditOutcome.FAILURE);
    }
}