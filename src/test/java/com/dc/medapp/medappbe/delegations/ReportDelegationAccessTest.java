package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.audit.AuditLogRepository;
import com.dc.medapp.medappbe.audit.AuditOutcome;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.delegations.dto.CreateReportDelegationRequest;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.security.JwtService;
import com.dc.medapp.medappbe.support.ApiTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not {@code @Transactional}: the counter of the wrong codes is saved in a transaction of its own, which would not
 * see rows that a test transaction has not committed yet. Every test uses its own patient and its own delegations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ReportDelegationAccessTest {

    private static final String BASE_PATH = "/api/v1/report-delegations";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ReportDelegationRepository delegationRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JwtService jwtService;

    private ApiTestClient api;
    private String patientToken;

    private record Link(long id, String token, String code) {}

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        patientToken = api.registerPatient("deleg.access." + UUID.randomUUID() + "@test.it");
    }

    private Link createLink() throws Exception {
        var json = api.postAs(patientToken, BASE_PATH, new CreateReportDelegationRequest("Luigi Neri", "luigi@example.com", null, true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tree = objectMapper.readTree(json);
        return new Link(tree.get("id").asLong(), tree.get("token").asString(), tree.get("accessCode").asString());
    }

    private ResultActions unlock(Link link, String code) throws Exception {
        return mockMvc.perform(post(BASE_PATH + "/" + link.token() + "/access").contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("code", code))));
    }

    private ResultActions reports(Link link, String accessToken) throws Exception {
        var request = get(BASE_PATH + "/" + link.token() + "/medical-reports");
        if (accessToken != null) {
            request.header(ApiTestClient.DELEGATION_ACCESS_HEADER, accessToken);
        }
        return mockMvc.perform(request);
    }

    /** The right code with its first character changed. */
    private static String wrong(Link link) {
        var first = link.code().charAt(0) == 'A' ? 'B' : 'A';
        return first + link.code().substring(1);
    }

    private void failTimes(Link link, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            unlock(link, wrong(link)).andExpect(status().isForbidden());
        }
    }

    // ---- reading needs the access token ---------------------------------------------------------

    @Test
    void theLinkAlone_isNotEnoughToReadTheReports() throws Exception {
        var link = createLink();

        reports(link, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_CODE_REQUIRED"));
        reports(link, "not-a-token").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_CODE_REQUIRED"));
    }

    @Test
    void theRightCode_givesAnAccessTokenThatOpensTheReports() throws Exception {
        var link = createLink();

        var json = unlock(link, link.code())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(15 * 60))
                .andReturn().getResponse().getContentAsString();
        var accessToken = objectMapper.readTree(json).get("accessToken").asString();

        reports(link, accessToken).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theCodeCanBeTypedInAnyCase_withOrWithoutTheDashAndWithSpaces() throws Exception {
        var link = createLink();
        var plain = link.code().replace("-", "");

        unlock(link, plain.toLowerCase()).andExpect(status().isOk());
        unlock(link, " " + plain.substring(0, 4).toLowerCase() + " - " + plain.substring(4) + " ").andExpect(status().isOk());
    }

    @Test
    void aWrongCode_returns403_andTheDelegationStaysClosed() throws Exception {
        var link = createLink();

        unlock(link, wrong(link))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"));
        assertThat(delegationRepository.findById(link.id()).orElseThrow().getFailedCodeAttempts()).isEqualTo(1);
    }

    @Test
    void aCodeLongerThanTheRealOne_isWrong_notCutByBcrypt() throws Exception {
        var link = createLink();

        unlock(link, link.code() + "ZZZZ").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"));
    }

    @Test
    void aBlankOrTooLongCode_returns400() throws Exception {
        var link = createLink();

        unlock(link, "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        unlock(link, "A".repeat(33)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---- the lock -------------------------------------------------------------------------------

    @Test
    void fiveWrongCodes_lockTheDelegation_evenForTheRightOne() throws Exception {
        var link = createLink();
        failTimes(link, 5);

        var response = unlock(link, link.code())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCESS_CODE_LOCKED"))
                .andReturn().getResponse();

        assertThat(Long.parseLong(response.getHeader("Retry-After"))).isBetween(1L, 15L * 60);
    }

    @Test
    void fourWrongCodes_doNotLockYet() throws Exception {
        var link = createLink();
        failTimes(link, 4);

        unlock(link, link.code()).andExpect(status().isOk());
    }

    @Test
    void aRightCode_clearsTheCount() throws Exception {
        var link = createLink();
        failTimes(link, 4);
        unlock(link, link.code()).andExpect(status().isOk());

        failTimes(link, 4);
        unlock(link, wrong(link)).andExpect(status().isForbidden());
    }

    @Test
    void whenTheLockIsOver_theRightCodeWorksAgain() throws Exception {
        var link = createLink();
        failTimes(link, 5);
        var delegation = delegationRepository.findById(link.id()).orElseThrow();
        delegation.setLockedUntil(Instant.now().minusSeconds(1));
        delegationRepository.save(delegation);

        unlock(link, link.code()).andExpect(status().isOk());
        assertThat(delegationRepository.findById(link.id()).orElseThrow().getLockedUntil()).isNull();
    }

    @Test
    void theLockOfADelegation_doesNotTouchTheOthers() throws Exception {
        var locked = createLink();
        var other = createLink();
        failTimes(locked, 5);

        unlock(other, other.code()).andExpect(status().isOk());
    }

    // ---- who can and cannot ---------------------------------------------------------------------

    @Test
    void anUnknownOrRevokedLink_returns404_whateverTheCode() throws Exception {
        var link = createLink();
        mockMvc.perform(post(BASE_PATH + "/not-a-token/access").contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("code", "AAAA-AAAA"))))
                .andExpect(status().isNotFound());

        api.postAs(patientToken, BASE_PATH + "/" + link.id() + "/revoke", null).andExpect(status().isOk());

        unlock(link, link.code()).andExpect(status().isNotFound());
    }

    @Test
    void aDelegationRevokedAfterTheUnlock_stopsAtOnce() throws Exception {
        var link = createLink();
        var accessToken = api.unlockDelegation(link.token(), link.code());
        reports(link, accessToken).andExpect(status().isOk());

        api.postAs(patientToken, BASE_PATH + "/" + link.id() + "/revoke", null).andExpect(status().isOk());

        reports(link, accessToken).andExpect(status().isNotFound());
    }

    @Test
    void aDelegationCreatedBeforeTheCodeExisted_cannotBeOpened() throws Exception {
        var patient = patientRepository.findAll().stream().findFirst().orElseThrow();
        var legacy = delegationRepository.save(ReportDelegationEntity.builder()
                .patient(patient).delegateName("Vecchio").delegateEmail("vecchio@example.com")
                .token(UUID.randomUUID().toString()).revoked(false).build());
        var link = new Link(legacy.getId(), legacy.getToken(), "AAAA-AAAA");

        unlock(link, "AAAA-AAAA").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"));
    }

    @Test
    void theAccessTokenOfAnotherDelegation_isRefused() throws Exception {
        var mine = createLink();
        var other = createLink();
        var othersAccess = api.unlockDelegation(other.token(), other.code());

        reports(mine, othersAccess).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_CODE_REQUIRED"));
    }

    @Test
    void aLoginToken_isNotAnAccessToken() throws Exception {
        var link = createLink();

        reports(link, patientToken).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_CODE_REQUIRED"));
    }

    @Test
    void anAccessToken_isNotALoginToken() throws Exception {
        var link = createLink();
        var accessToken = api.unlockDelegation(link.token(), link.code());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredAccessToken_isRefused() throws Exception {
        var link = createLink();
        var expired = jwtService.generateDelegationAccessToken(link.id(), Duration.ofSeconds(-5));

        reports(link, expired).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_CODE_REQUIRED"));
    }

    @Test
    void theAttachmentsNeedTheAccessTokenToo() throws Exception {
        var link = createLink();
        var accessToken = api.unlockDelegation(link.token(), link.code());
        var path = BASE_PATH + "/" + link.token() + "/medical-reports/1/attachment";

        mockMvc.perform(get(path)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_CODE_REQUIRED"));
        mockMvc.perform(get(path).header(ApiTestClient.DELEGATION_ACCESS_HEADER, accessToken)).andExpect(status().isNotFound());
    }

    // ---- the access log -------------------------------------------------------------------------

    @Test
    void everyUnlock_isInTheAccessLog_withTheDelegationId_andNeverTheCode() throws Exception {
        var link = createLink();
        unlock(link, wrong(link)).andExpect(status().isForbidden());
        unlock(link, link.code()).andExpect(status().isOk());

        var lines = auditLogRepository.findAll().stream()
                .filter(line -> "UNLOCK_REPORT_DELEGATION".equals(line.getAction()) && Long.valueOf(link.id()).equals(line.getEntityId()))
                .toList();
        assertThat(lines).extracting("outcome").containsExactlyInAnyOrder(AuditOutcome.FAILURE, AuditOutcome.SUCCESS);
        assertThat(lines).extracting("failureReason").contains("InvalidAccessCodeException");
        assertThat(lines.toString()).doesNotContain(link.code().replace("-", ""));
    }
}