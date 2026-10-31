package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.auth.dto.ChangePasswordRequest;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.dc.medapp.medappbe.support.ApiTestClient.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LoginLockoutTest {

    private static final String WRONG = "una-password-sbagliata-a-caso";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LoginAttemptRepository attemptsRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DoctorRepository doctorRepo;

    @BeforeEach
    void setUp() {
        attemptsRepo.deleteAll();
    }

    private void createUser(String email) {
        userRepository.save(UserEntity.builder().email(email).passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.PATIENT).build());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private void failTimes(String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            login(email, WRONG).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void fiveWrongPasswords_lockTheAccount_evenForTheRightOne() throws Exception {
        createUser("lock.five@test.it");
        failTimes("lock.five@test.it", 5);

        var response = login("lock.five@test.it", ApiTestClient.PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andReturn().getResponse();

        long retryAfter = Long.parseLong(response.getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 15L * 60);
    }

    @Test
    void fourWrongPasswords_doNotLockYet() throws Exception {
        createUser("lock.four@test.it");
        failTimes("lock.four@test.it", 4);

        login("lock.four@test.it", ApiTestClient.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void whileLocked_thePasswordIsNotEvenChecked_soTheLockDoesNotMove() throws Exception {
        createUser("lock.still@test.it");
        failTimes("lock.still@test.it", 5);
        var before = attemptsRepo.findByEmail("lock.still@test.it").orElseThrow().getLockedUntil();

        login("lock.still@test.it", ApiTestClient.PASSWORD).andExpect(status().isTooManyRequests());
        login("lock.still@test.it", WRONG).andExpect(status().isTooManyRequests());

        assertThat(attemptsRepo.findByEmail("lock.still@test.it").orElseThrow().getLockedUntil()).isEqualTo(before);
    }

    @Test
    void anAddressThatDoesNotExistIsLockedTooAndAnswersTheSame() throws Exception {
        // the same answers for an address that exists and one that does not: nothing tells which accounts are real
        failTimes("nobody.here@test.it", 5);

        login("nobody.here@test.it", WRONG)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void theLockEndsOnItsOwn_andTheRecordIsCleared() throws Exception {
        createUser("lock.ends@test.it");
        attemptsRepo.save(LoginAttemptEntity.builder().email("lock.ends@test.it").failures(0)
                .lockedUntil(Instant.now().minusSeconds(5)).lastFailureAt(Instant.now().minusSeconds(900)).build());

        login("lock.ends@test.it", ApiTestClient.PASSWORD).andExpect(status().isOk());

        assertThat(attemptsRepo.findByEmail("lock.ends@test.it")).isEmpty();
    }

    // ---- the count ------------------------------------------------------------------------------

    @Test
    void aGoodLogin_clearsTheCount() throws Exception {
        createUser("lock.reset@test.it");
        failTimes("lock.reset@test.it", 4);
        login("lock.reset@test.it", ApiTestClient.PASSWORD).andExpect(status().isOk());

        failTimes("lock.reset@test.it", 4);

        login("lock.reset@test.it", ApiTestClient.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theCountIsKept_evenThoughTheChangeOfThePasswordIsRolledBack() throws Exception {
        // the change of the password is one transaction, and a wrong current password ends it with an exception
        var api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepo);
        var token = api.registerPatient("lock.rollback@test.it");

        api.postAs(token, "/api/v1/auth/change-password", new ChangePasswordRequest("una-password-sbagliata", ApiTestClient.CHANGED_PASSWORD))
                .andExpect(status().isBadRequest());

        assertThat(attemptsRepo.findByEmail("lock.rollback@test.it").orElseThrow().getFailures()).isEqualTo(1);
    }

    @Test
    void theCountIsKept_evenThoughTheLoginEndsWithAnException() throws Exception {
        createUser("lock.kept@test.it");

        failTimes("lock.kept@test.it", 2);

        assertThat(attemptsRepo.findByEmail("lock.kept@test.it").orElseThrow().getFailures()).isEqualTo(2);
    }

    @Test
    void failuresOlderThanTheLockWindowDoNotCount() throws Exception {
        createUser("lock.old@test.it");
        attemptsRepo.save(LoginAttemptEntity.builder().email("lock.old@test.it").failures(4)
                .lastFailureAt(Instant.now().minus(20, ChronoUnit.MINUTES)).build());

        login("lock.old@test.it", WRONG).andExpect(status().isUnauthorized());

        var attempt = attemptsRepo.findByEmail("lock.old@test.it").orElseThrow();
        assertThat(attempt.getFailures()).isEqualTo(1);
        assertThat(attempt.getLockedUntil()).isNull();
    }

    @Test
    void theCaseOfTheAddressDoesNotMatterForTheCount() throws Exception {
        createUser("lock.case@test.it");
        for (var email : new String[]{"LOCK.CASE@test.it", "Lock.Case@Test.it", "lock.case@test.it", " lock.case@test.it ", "LOCK.case@TEST.it"}) {
            login(email, WRONG).andExpect(status().isUnauthorized());
        }

        login("lock.case@test.it", ApiTestClient.PASSWORD).andExpect(status().isTooManyRequests());
        assertThat(attemptsRepo.findAll()).hasSize(1);
    }

    @Test
    void aPasswordLongerThan72Bytes_isAWrongPassword_evenIfItStartsLikeTheRealOne() throws Exception {
        // BCrypt cuts what it is given at 72 bytes: without the guard, the exact password plus anything would work
        var exact = "tavolo-viola-nuvola-finestra-bicicletta-montagna-ruota-mela-pera-uva-lim";
        assertThat(exact.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
        userRepository.save(UserEntity.builder().email("lock.long@test.it").passwordHash(passwordEncoder.encode(exact)).role(Role.PATIENT).build());

        login("lock.long@test.it", exact + "-and-something-more")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertThat(attemptsRepo.findByEmail("lock.long@test.it").orElseThrow().getFailures()).isEqualTo(1);
        login("lock.long@test.it", exact).andExpect(status().isOk());
    }

    @Test
    void aDisabledAccountCountsAndAnswersLikeAWrongPassword() throws Exception {
        var user = userRepository.save(UserEntity.builder().email("lock.off@test.it")
                .passwordHash(passwordEncoder.encode(ApiTestClient.PASSWORD)).role(Role.PATIENT).enabled(false).build());

        login(user.getEmail(), ApiTestClient.PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertThat(attemptsRepo.findByEmail("lock.off@test.it").orElseThrow().getFailures()).isEqualTo(1);
    }
}
