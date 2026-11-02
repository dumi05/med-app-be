package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import com.dc.medapp.medappbe.support.ApiTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static com.dc.medapp.medappbe.support.ApiTestClient.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void register_thenLogin() throws Exception {
        RegisterRequest req = new RegisterRequest("mario.rossi@test.com",PASSWORD, "Mario", "Rossi" , LocalDate.of(1990, 1, 1), true, "v1.0");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        LoginRequest loginReq = new LoginRequest("mario.rossi@test.com", PASSWORD);

        String responseJson = mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(responseJson).get("token").asString();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("mario.rossi@test.com"));
    }

    @Test
    void register_returnBadRequest_withoutGdprConsent() throws Exception {
        RegisterRequest req = new RegisterRequest("mario.rossi@test.com",PASSWORD, "Mario", "Rossi" , LocalDate.of(1990, 1, 1), false, "v1.0");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returnBadRequest_whenMethodInvalidArguments() throws Exception {
        RegisterRequest req = new RegisterRequest("non-e-una-mail", "", "Test", "Errore", LocalDate.of(1999,5,5), true, "v1.0");

        String body = mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("email: ").contains("password: ");
        assertThat(body).contains("; ");
        assertThat(body).doesNotContain("non-e-una-mail");
    }

    private ResultActions register(String email, String password) throws Exception {
        var req = new RegisterRequest(email, password, "Mario", "Verdi", LocalDate.of(1990, 1, 1), true, "v1.0");
        return mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content(objectMapper.writeValueAsString(req)));
    }

    @Test
    void register_withAShortPassword_returns400WeakPassword() throws Exception {
        register("weak.short@test.com", "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.message").value(containsString("almeno 12 caratteri")));
    }

    @Test
    void register_withACommonPassword_returns400WeakPassword() throws Exception {
        register("weak.common@test.com", "Password2026!!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.message").value(containsString("troppo comune")));
    }

    @Test
    void register_withAPasswordBuiltOnTheEmail_returns400WeakPassword() throws Exception {
        register("giulia.bianchi@test.com", "evviva-giuliabianchi-2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void register_withAPasswordOver256Characters_returns400ValidationFailed() throws Exception {
        register("weak.long@test.com", "x7".repeat(129))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void register_withAnEmailWithoutADotInTheDomain_returns400ValidationFailed() throws Exception {
        register("mario@localhost", ApiTestClient.PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("email: ")));
    }

    @Test
    void register_savesTheEmailInLowerCase_andTheLoginDoesNotCareAboutTheCase() throws Exception {
        register("  Mario.Verdi@Test.COM ", ApiTestClient.PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("mario.verdi@test.com"));

        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("MARIO.VERDI@test.com", ApiTestClient.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("mario.verdi@test.com"));
    }

    @Test
    void login_doesNotApplyThePasswordRules_soAnOldWeakPasswordStillWorks() throws Exception {
        var user = userRepository.save(UserEntity.builder()
                .email("old.weak@test.com").passwordHash(passwordEncoder.encode("abc")).role(Role.PATIENT).build());

        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), "abc"))))
                .andExpect(status().isOk());
    }

    @Test
    void login_fakeEmail_throwsInvalidCredentialsException() throws Exception {
        LoginRequest loginReq = new LoginRequest("mario.arancioni@test.com", PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsException() throws Exception {
        RegisterRequest req = new RegisterRequest("claudio.rossi@test.com",PASSWORD, "Mario", "Rossi" , LocalDate.of(1990, 1, 1), true, "v1.0");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        LoginRequest loginReq = new LoginRequest("claudio.rossi@test.com", "password1234");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void register_withTheSameEmailTwice_returns409() throws Exception {
        register("mario.bianchi@test.com", ApiTestClient.PASSWORD).andExpect(status().isOk());

        register("MARIO.BIANCHI@test.com", ApiTestClient.PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"));
    }

    @Test
    void register_withAnEmailWithADotInTheName_thenWithTheSameOne_returns409() throws Exception {
        register("pippo.baudo@gmail.com", ApiTestClient.PASSWORD).andExpect(status().isOk());

        register("PIPPO.BAUDO@gmail.com", ApiTestClient.PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"));
    }
}