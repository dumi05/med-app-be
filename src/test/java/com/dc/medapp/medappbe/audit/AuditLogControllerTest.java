package com.dc.medapp.medappbe.audit;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.reports.dto.CreateMedicalReportRequest;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import com.dc.medapp.medappbe.support.ApiTestClient;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AuditLogControllerTest {
    private static final String URL = "/api/v1/audit-logs";
    private static final String DOCTOR_EMAIL = "audit.doc@test.it";
    private static final String PATIENT_EMAIL = "audit.patient@test.it";
    private static final String OTHER_PATIENT_EMAIL = "audit.other@test.it";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private PriceListRepository priceListRepository;
    @Autowired
    private AvailabilitySlotRepository slotRepository;

    private ApiTestClient api;
    private DoctorEntity doctor;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-auditlog-test").build());
        priceListRepository.save(PriceListEntity.builder()
                .specialization(specialization).visitType("Visita specialistica").price(new BigDecimal("100.00")).build());
        doctor = api.createDoctorWithConsentFlag(DOCTOR_EMAIL, false, specialization);
        api.createUser("audit.admin@test.it", Role.ADMIN);
        adminToken = api.login("audit.admin@test.it");
    }

    @Test
    void admin_seesTheWholeLifeOfAVisitInTheLog_includingTheDeniedRead() throws Exception {
        var patientToken = api.registerPatient(PATIENT_EMAIL);
        var otherPatientToken = api.registerPatient(OTHER_PATIENT_EMAIL);
        var doctorToken = api.login(DOCTOR_EMAIL);

        var appointmentId = api.book(patientToken, newSlot().getId());
        api.postAs(doctorToken, "/api/v1/appointments/" + appointmentId + "/complete", null).andExpect(status().isOk());
        var reportJson = api.postReport(doctorToken, new CreateMedicalReportRequest(appointmentId, "Tutto regolare"), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var reportId = objectMapper.readTree(reportJson).get("id").asLong();
        api.getAs(patientToken, "/api/v1/medical-reports/" + reportId).andExpect(status().isOk());
        api.getAs(otherPatientToken, "/api/v1/medical-reports/" + reportId).andExpect(status().isForbidden());

        api.getAs(adminToken, URL + "?limit=100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'BOOK_APPOINTMENT' && @.userEmail == '" + PATIENT_EMAIL + "' && @.outcome == 'SUCCESS')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action == 'COMPLETE_VISIT' && @.userEmail == '" + DOCTOR_EMAIL + "' && @.outcome == 'SUCCESS')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action == 'WRITE_REPORT' && @.userEmail == '" + DOCTOR_EMAIL + "' && @.outcome == 'SUCCESS')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action == 'ISSUE_INVOICE' && @.outcome == 'SUCCESS')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action == 'READ_REPORT' && @.userEmail == '" + PATIENT_EMAIL + "' && @.outcome == 'SUCCESS')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action == 'READ_REPORT' && @.userEmail == '" + OTHER_PATIENT_EMAIL + "' && @.outcome == 'FAILURE' && @.failureReason == 'UnauthorizedAccessException' && @.entityId == " + reportId + ")]").isNotEmpty());
    }

    @Test
    void log_isReturnedNewestFirst_andTheLimitIsRespected() throws Exception {
        var patientToken = api.registerPatient(PATIENT_EMAIL);
        var appointmentId = api.book(patientToken, newSlot().getId());
        api.postAs(patientToken, "/api/v1/appointments/" + appointmentId + "/cancel", null).andExpect(status().isOk());

        api.getAs(adminToken, URL + "?limit=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("CANCEL_APPOINTMENT"));
    }

    @Test
    void nonAdmin_cannotReadTheLog_receives403() throws Exception {
        api.getAs(api.registerPatient(PATIENT_EMAIL), URL).andExpect(status().isForbidden());
        api.getAs(api.login(DOCTOR_EMAIL), URL).andExpect(status().isForbidden());
    }

    @Test
    void anonymous_receives401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private AvailabilitySlotEntity newSlot() {
        return slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor)
                .startTime(LocalDateTime.now().plusDays(2)).durationMinutes(30).booked(false).build());
    }
}