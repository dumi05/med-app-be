package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.delegations.dto.CreateReportDelegationRequest;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.reports.dto.CreateMedicalReportRequest;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import com.dc.medapp.medappbe.support.ApiTestClient;
import com.dc.medapp.medappbe.support.TestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class ReportDelegationAttachmentTest {

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
    private SpecializationEntity specialization;
    private DoctorEntity doctor;
    private String doctorToken;
    private String patientToken;
    private long reportWithFile;
    // Every visit one hour after the previous: the visits of the same patient must not overlap.
    private int visits;
    private final Map<String, String> accessTokens = new java.util.HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-deleg-att-test").build());
        priceListRepository.save(PriceListEntity.builder()
                .specialization(specialization).visitType(BillingService.STANDARD_VISIT_TYPE).price(new BigDecimal("100.00")).build());
        doctor = api.createDoctorWithConsentFlag("deleg.att.doctor@test.it", false, specialization);
        doctorToken = api.login("deleg.att.doctor@test.it");
        patientToken = api.registerPatient("deleg.att.patient@test.it");
        reportWithFile = writeReport(patientToken, TestFiles.pdf());
    }

    private long writeReport(String forPatientToken, MockMultipartFile file) throws Exception {
        var slot = slotRepository.save(AvailabilitySlotEntity.builder()
                .doctor(doctor).startTime(LocalDateTime.now().plusDays(1).plusHours(visits++)).durationMinutes(30).booked(false).build());
        var appointmentId = api.book(forPatientToken, slot.getId());
        api.postAs(doctorToken, "/api/v1/appointments/" + appointmentId + "/complete", null).andExpect(status().isOk());
        var json = api.postReport(doctorToken, new CreateMedicalReportRequest(appointmentId, "Nessun risultato."), file)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private String delegationToken(boolean includeAttachments) throws Exception {
        var json = api.postAs(patientToken, BASE_PATH, new CreateReportDelegationRequest("Luigi Neri", "luigi@example.com", null, includeAttachments))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var jsonObj = objectMapper.readTree(json);
        var token = jsonObj.get("token").asString();
        accessTokens.put(token, api.unlockDelegation(token, jsonObj.get("accessCode").asString()));
        return token;
    }

    private String attachmentPath(String token, long reportId) {
        return BASE_PATH + "/" + token + "/medical-reports/" + reportId + "/attachment";
    }

    // ---- the choice of the patient --------------------------------------------------------------

    @Test
    void theDelegationDoesNotIncludeTheAttachmentsUnlessThePatientAsks() throws Exception {
        api.postAs(patientToken, BASE_PATH, new CreateReportDelegationRequest("Luigi Neri", "luigi@example.com", null, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.includeAttachments").value(false));
        api.postAs(patientToken, BASE_PATH, new CreateReportDelegationRequest("Anna Neri", "anna@example.com", null, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.includeAttachments").value(true));
    }

    @Test
    void theFlagCanBeLeftOutOfTheRequest_andMeansNo() throws Exception {
        api.postAs(patientToken, BASE_PATH, Map.of("delegateName", "Luigi Neri", "delegateEmail", "luigi@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.includeAttachments").value(false));
    }

    // ---- the page of the caregiver --------------------------------------------------------------

    @Test
    void theReportsTellWhenAnAttachmentCanBeDownloaded() throws Exception {
        var withAttachments = delegationToken(true);
        var without = delegationToken(false);

        mockMvc.perform(get(BASE_PATH + "/" + withAttachments + "/medical-reports").header(ApiTestClient.DELEGATION_ACCESS_HEADER, access(withAttachments)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reportWithFile))
                .andExpect(jsonPath("$[0].attachmentAvailable").value(true))
                .andExpect(jsonPath("$[0].attachmentUrl").doesNotExist());
        mockMvc.perform(get(BASE_PATH + "/" + without + "/medical-reports").header(ApiTestClient.DELEGATION_ACCESS_HEADER, access(without)))
                .andExpect(jsonPath("$[0].attachmentAvailable").value(false));
    }

    @Test
    void aReportWithoutFile_isNeverAvailable() throws Exception {
        var token = delegationToken(true);
        var noFile = writeReport(patientToken, null);

        mockMvc.perform(get(BASE_PATH + "/" + token + "/medical-reports").header(ApiTestClient.DELEGATION_ACCESS_HEADER, access(token)))
                .andExpect(jsonPath("$[?(@.id == " + noFile + ")].attachmentAvailable").value(false));
    }

    // ---- the download ---------------------------------------------------------------------------

    @Test
    void whoHoldsTheToken_downloadsTheFile_withoutLoggingIn() throws Exception {
        var token = delegationToken(true);

        mockMvc.perform(get(attachmentPath(token, reportWithFile)).header(ApiTestClient.DELEGATION_ACCESS_HEADER, access(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(TestFiles.pdfBytes()));
    }

    @Test
    void withoutTheChoiceOfThePatient_returns404() throws Exception {
        var token = delegationToken(false);

        mockMvc.perform(get(attachmentPath(token, reportWithFile)).header(ApiTestClient.DELEGATION_ACCESS_HEADER, access(token))).andExpect(status().isNotFound());
    }

    @Test
    void aRevokedLink_returns404() throws Exception {
        var json = api.postAs(patientToken, BASE_PATH, new CreateReportDelegationRequest("Luigi Neri", "luigi@example.com", null, true))
                .andReturn().getResponse().getContentAsString();
        var id = objectMapper.readTree(json).get("id").asLong();
        var token = objectMapper.readTree(json).get("token").asString();
        api.postAs(patientToken, BASE_PATH + "/" + id + "/revoke", null).andExpect(status().isOk());

        mockMvc.perform(get(attachmentPath(token, reportWithFile))).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownToken_returns404() throws Exception {
        mockMvc.perform(get(attachmentPath("not-a-token", reportWithFile))).andExpect(status().isNotFound());
    }

    @Test
    void theReportOfAnotherPatient_returns404_likeAnythingElseThatIsMissing() throws Exception {
        var token = delegationToken(true);
        var otherToken = api.registerPatient("deleg.att.other@test.it");
        var othersReport = writeReport(otherToken, TestFiles.pdf());

        mockMvc.perform(get(attachmentPath(token, othersReport)).header(ApiTestClient.DELEGATION_ACCESS_HEADER, access(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void aReportWithoutFile_returns404() throws Exception {
        var token = delegationToken(true);
        var noFile = writeReport(patientToken, null);

        mockMvc.perform(get(attachmentPath(token, noFile)).header(ApiTestClient.DELEGATION_ACCESS_HEADER, access(token))).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownReport_returns404() throws Exception {
        var token = delegationToken(true);

        mockMvc.perform(get(attachmentPath(token, 999_999L)).header(ApiTestClient.DELEGATION_ACCESS_HEADER, access(token))).andExpect(status().isNotFound());
    }

    @Test
    void theLinkOpensNothingElse() throws Exception {
        var token = delegationToken(true);

        mockMvc.perform(get("/api/v1/medical-reports/" + reportWithFile + "/attachment").header(ApiTestClient.DELEGATION_ACCESS_HEADER, access(token))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE_PATH + "/" + token + "/medical-reports/" + reportWithFile).header(ApiTestClient.DELEGATION_ACCESS_HEADER, access(token))).andExpect(status().isUnauthorized());
    }

    @Test
    void everyAttempt_isInTheAccessLog_withTheIdOfTheDelegation() throws Exception {
        var json = api.postAs(patientToken, BASE_PATH, new CreateReportDelegationRequest("Luigi Neri", "luigi@example.com", null, true))
                .andReturn().getResponse().getContentAsString();
        var token = objectMapper.readTree(json).get("token").asString();
        var delegationId = objectMapper.readTree(json).get("id").asLong();
        accessTokens.put(token, api.unlockDelegation(token, objectMapper.readTree(json).get("accessCode").asString()));
        api.createUser("deleg.att.admin@test.it", Role.ADMIN);

        mockMvc.perform(get(attachmentPath(token, reportWithFile)).header(ApiTestClient.DELEGATION_ACCESS_HEADER, access(token))).andExpect(status().isOk());
        mockMvc.perform(get(attachmentPath("not-a-token", reportWithFile))).andExpect(status().isNotFound());

        api.getAs(api.login("deleg.att.admin@test.it"), "/api/v1/audit-logs?limit=200")
                .andExpect(jsonPath("$[?(@.action == 'ACCESS_ATTACHMENT_VIA_DELEGATION' && @.outcome == 'SUCCESS' && @.entityId == " + delegationId + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action == 'ACCESS_ATTACHMENT_VIA_DELEGATION' && @.outcome == 'FAILURE')]").isNotEmpty());
    }

    @Test
    void whoHoldsTheToken_downloadsAWordDocumentAsAnAttachment() throws Exception {
        var wordReport = writeReport(patientToken, TestFiles.docx());
        var token = delegationToken(true);

        mockMvc.perform(get(attachmentPath(token, wordReport)).header(ApiTestClient.DELEGATION_ACCESS_HEADER, access(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("referto-" + wordReport + ".docx")))
                .andExpect(content().bytes(TestFiles.docxBytes()));
    }

    private String access(String token) {
        return accessTokens.getOrDefault(token, "");
    }
 }