package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.audit.AuditLogRepository;
import com.dc.medapp.medappbe.audit.AuditOutcome;
import com.dc.medapp.medappbe.auth.dto.ChangePasswordRequest;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.support.ApiTestClient;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ChangePasswordTest {

    private static final String CURRENT = ApiTestClient.PASSWORD;
    private static final String NEW = ApiTestClient.CHANGED_PASSWORD;
    private static final String PATH = "/api/v1/auth/change-password";

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
    private LoginAttemptRepository attempts;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private EntityManager entityManager;

    private ApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
    }

    /**
     * The whole test is one transaction, while every request of the real application is its own: what the counter
     * writes in its transaction would stay invisible behind the copy that this one already read.
     */
    private void startANewRequest() {
        entityManager.flush();
        entityManager.clear();
    }

    private ResultActions change(String token, String current, String next) throws Exception {
        return api.postAs(token, PATH, new ChangePasswordRequest(current, next));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    // ---- the change -----------------------------------------------------------------------------

    @Test
    void changePassword_returnsANewToken_andTheOldOneStopsWorking() throws Exception {
        var oldToken = api.registerPatient("change.ok@test.it");

        var response = change(oldToken, CURRENT, NEW)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("change.ok@test.it"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();
        var newToken = objectMapper.readTree(response).get("token").asString();

        api.getAs(oldToken, "/api/v1/auth/me").andExpect(status().isUnauthorized());
        api.getAs(newToken, "/api/v1/auth/me").andExpect(status().isOk());
        assertThat(userRepository.findByEmailIgnoreCase("change.ok@test.it").orElseThrow().getTokenVersion()).isEqualTo(1);
    }

    @Test
    void afterTheChange_theOldPasswordIsRefused_andTheNewOneWorks() throws Exception {
        var token = api.registerPatient("change.login@test.it");
        change(token, CURRENT, NEW).andExpect(status().isOk());

        login("change.login@test.it", CURRENT).andExpect(status().isUnauthorized());
        login("change.login@test.it", NEW).andExpect(status().isOk());
    }

    @Test
    void anAdminHasNoProfile_andCanChangeThePasswordToo() throws Exception {
        api.createUser("change.admin@test.it", Role.ADMIN);
        var token = api.login("change.admin@test.it");

        change(token, CURRENT, NEW).andExpect(status().isOk());
    }

    // ---- the current password -------------------------------------------------------------------

    @Test
    void aWrongCurrentPassword_returns400_andIsCounted() throws Exception {
        var token = api.registerPatient("change.wrong@test.it");

        change(token, "una-password-sbagliata", NEW)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        assertThat(attempts.findByEmail("change.wrong@test.it").orElseThrow().getFailures()).isEqualTo(1);
    }

    @Test
    void fiveWrongCurrentPasswords_lockTheAccount_forTheChangeAndForTheLogin() throws Exception {
        var token = api.registerPatient("change.lock@test.it");
        for (int i = 0; i < 5; i++) {
            startANewRequest();
            change(token, "una-password-sbagliata", NEW).andExpect(status().isBadRequest());
        }

        startANewRequest();
        change(token, CURRENT, NEW)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        startANewRequest();
        login("change.lock@test.it", CURRENT).andExpect(status().isTooManyRequests());
    }

    @Test
    void aCurrentPasswordLongerThan72Bytes_isWrong_evenIfItStartsLikeTheRealOne() throws Exception {
        var exact = "tavolo-viola-nuvola-finestra-bicicletta-montagna-ruota-mela-pera-uva-lim";
        userRepository.save(UserEntity.builder().email("change.long@test.it").passwordHash(passwordEncoder.encode(exact)).role(Role.ADMIN).build());
        var token = objectMapper.readTree(login("change.long@test.it", exact).andReturn().getResponse().getContentAsString()).get("token").asString();

        change(token, exact + "-and-something-more", NEW)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }
    // ---- the new password -----------------------------------------------------------------------

    @Test
    void aWeakNewPassword_returns400WeakPassword() throws Exception {
        var token = api.registerPatient("change.weak@test.it");

        change(token, CURRENT, "corta").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        change(token, CURRENT, "password1234").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void theNewPasswordMustDifferFromTheCurrentOne() throws Exception {
        var token = api.registerPatient("change.same@test.it");

        change(token, CURRENT, CURRENT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.message").value(containsString("diversa")));
    }

    @Test
    void theNewPasswordCannotBeBuiltOnTheNameOfTheProfile() throws Exception {
        // the patient of registerPatient is "Luca Amauri"
        var token = api.registerPatient("change.name@test.it");

        change(token, CURRENT, "evviva-luca-amauri-26")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.message").value(containsString("nome")));
    }

    @Test
    void aNewPasswordOver72Bytes_isRefused() throws Exception {
        var token = api.registerPatient("change.big@test.it");

        change(token, CURRENT, "tavolo-viola-nuvola-finestra-bicicletta-montagna-ruota-mela-pera-uva-limone")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.message").value(containsString("72")));
    }

    @Test
    void aBlankNewPassword_returns400ValidationFailed() throws Exception {
        var token = api.registerPatient("change.blank@test.it");

        change(token, CURRENT, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---- who can ask ----------------------------------------------------------------------------

    @Test
    void withoutAToken_returns401() throws Exception {
        mockMvc.perform(post(PATH).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest(CURRENT, NEW))))
                .andExpect(status().isUnauthorized());
    }

    // ---- the access log -------------------------------------------------------------

    @Test
    void everyAttemptIsInTheAccessLog_successAndFailure() throws Exception {
        var token = api.registerPatient("change.audit@test.it");
        change(token, "una-password-sbagliata", NEW).andExpect(status().isBadRequest());
        change(token, CURRENT, NEW).andExpect(status().isOk());

        var entries = auditLogRepository.findAll().stream()
                .filter(entry -> "CHANGE_PASSWORD".equals(entry.getAction()) && "change.audit@test.it".equals(entry.getUserEmail()))
                .toList();
        assertThat(entries).extracting(entry -> entry.getOutcome())
                .containsExactlyInAnyOrder(AuditOutcome.FAILURE, AuditOutcome.SUCCESS);
    }
}
