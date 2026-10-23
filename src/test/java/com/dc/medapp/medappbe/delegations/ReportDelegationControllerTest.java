package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.delegations.dto.CreateReportDelegationRequest;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class ReportDelegationControllerTest {

    private static final String BASE_PATH = "/api/v1/report-delegations";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private AvailabilitySlotRepository slotRepository;
    @Autowired
    private PriceListRepository priceListRepository;

    private ApiTestClient api;
    private String patientToken;
    private String doctorToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-test").build());
        priceListRepository.save(PriceListEntity.builder()
                .specialization(specialization).visitType(BillingService.STANDARD_VISIT_TYPE).price(new BigDecimal("100.00")).build());
        var doctor = api.createDoctorWithConsentFlag("doctor@test.it", false, specialization);
        doctorToken = api.login("doctor@test.it");
        patientToken = api.registerPatient("patient@test.it");

        writeReportForThePatient(doctor);
    }

    // ---- patient side ---------------------------------------------------------------------------

    @Test
    void patient_canCreateADelegation_andGetsTheToken() throws Exception {
        api.postAs(patientToken, BASE_PATH, request("Luigi Rossi", "luigi@example.com", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.delegateName").value("Luigi Rossi"))
                .andExpect(jsonPath("$.revoked").value(false))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void doctor_cannotCreateADelegation_receives403() throws Exception {
        api.postAs(doctorToken, BASE_PATH, request("Luigi Rossi", "luigi@example.com", null))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingADelegationWithoutToken_receives401() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType("application/json").content(objectMapper.writeValueAsString(request("A", "a@b.it", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingADelegationWithABlankName_receives400() throws Exception {
        api.postAs(patientToken, BASE_PATH, request(" ", "luigi@example.com", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void creatingADelegationWithAnInvalidEmail_receives400() throws Exception {
        api.postAs(patientToken, BASE_PATH, request("Luigi Rossi", "not-an-email", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void creatingADelegationExpiredYesterday_receives400() throws Exception {
        api.postAs(patientToken, BASE_PATH, request("Luigi Rossi", "luigi@example.com", LocalDate.now().minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void patient_seesOnlyTheirOwnDelegations() throws Exception {
        api.postAs(patientToken, BASE_PATH, request("Luigi Rossi", "luigi@example.com", null)).andExpect(status().isOk());
        var otherToken = api.registerPatient("other@test.it");
        api.postAs(otherToken, BASE_PATH, request("Altro", "altro@example.com", null)).andExpect(status().isOk());

        api.getAs(patientToken, BASE_PATH + "/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].delegateName").value("Luigi Rossi"));
    }

    @Test
    void listingTheDelegationsWithoutToken_receives401_andIsNotMistakenForTheCaregiverPage() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void patient_canRevokeTheirDelegation() throws Exception {
        var id = createDelegation(patientToken);

        api.postAs(patientToken, BASE_PATH + "/" + id + "/revoke", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(true))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void patient_cannotRevokeADelegationOfAnotherPatient_receives403() throws Exception {
        var id = createDelegation(patientToken);
        var otherToken = api.registerPatient("intruder@test.it");

        api.postAs(otherToken, BASE_PATH + "/" + id + "/revoke", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    void revokingAnUnknownDelegation_receives404() throws Exception {
        api.postAs(patientToken, BASE_PATH + "/999999/revoke", null)
                .andExpect(status().isNotFound());
    }

    // ---- caregiver side (public) ----------------------------------------------------------------

    @Test
    void whoHoldsTheToken_readsTheReports_withoutLoggingIn() throws Exception {
        var token = createDelegationToken(patientToken);

        mockMvc.perform(get(BASE_PATH + "/" + token + "/medical-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].diagnosisText").value("Nessun risultato."))
                .andExpect(jsonPath("$[0].doctorLastName").value("Cortelesi"))
                .andExpect(jsonPath("$[0].specialization").value("Cardiologia-test"));
    }

    @Test
    void thePublicResponse_doesNotExposeInternalIds() throws Exception {
        var token = createDelegationToken(patientToken);

        mockMvc.perform(get(BASE_PATH + "/" + token + "/medical-reports"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].appointmentId").doesNotExist());
    }

    @Test
    void thePublicResponse_isNotCached() throws Exception {
        var token = createDelegationToken(patientToken);

        mockMvc.perform(get(BASE_PATH + "/" + token + "/medical-reports"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void anUnknownToken_receives404() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/not-a-token/medical-reports"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void aRevokedToken_receives404() throws Exception {
        var id = createDelegation(patientToken);
        var token = api.getAs(patientToken, BASE_PATH + "/me").andReturn().getResponse().getContentAsString();
        var tokenValue = objectMapper.readTree(token).get(0).get("token").asString();
        api.postAs(patientToken, BASE_PATH + "/" + id + "/revoke", null).andExpect(status().isOk());

        mockMvc.perform(get(BASE_PATH + "/" + tokenValue + "/medical-reports"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theTokenOpensNothingElse() throws Exception {
        var token = createDelegationToken(patientToken);

        mockMvc.perform(get("/api/v1/medical-reports").param("appointmentId", "1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/appointments/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE_PATH + "/" + token)).andExpect(status().isUnauthorized());
    }

    // Helpers

    private CreateReportDelegationRequest request(String name, String email, LocalDate expiresAt) {
        return new CreateReportDelegationRequest(name, email, expiresAt);
    }

    private long createDelegation(String token) throws Exception {
        var json = api.postAs(token, BASE_PATH, request("Luigi Rossi", "luigi@example.com", null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private String createDelegationToken(String token) throws Exception {
        var json = api.postAs(token, BASE_PATH, request("Luigi Rossi", "luigi@example.com", null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asString();
    }

    private void writeReportForThePatient(DoctorEntity doctor) throws Exception {
        var slot = slotRepository.save(AvailabilitySlotEntity.builder()
                .doctor(doctor).startTime(LocalDateTime.now().plusDays(1)).durationMinutes(30).booked(false).build());
        var appointmentId = api.book(patientToken, slot.getId());
        api.postAs(doctorToken, "/api/v1/appointments/" + appointmentId + "/complete", null).andExpect(status().isOk());
        api.postAs(doctorToken, "/api/v1/medical-reports", new CreateMedicalReportRequest(appointmentId, "Nessun risultato.", null))
                .andExpect(status().isOk());
    }
}