package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
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
    private ObjectMapper objectMapper;

    @Test
    void register_thenLogin() throws Exception {
        RegisterRequest req = new RegisterRequest("mario.rossi@test.com","password123", "Mario", "Rossi" , LocalDate.of(1990, 1, 1), true, "v1.0");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        LoginRequest loginReq = new LoginRequest("mario.rossi@test.com", "password123");

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
        RegisterRequest req = new RegisterRequest("mario.rossi@test.com","password123", "Mario", "Rossi" , LocalDate.of(1990, 1, 1), false, "v1.0");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returnBadRequest_whenMethodInvalidArguments() throws Exception {
        RegisterRequest req = new RegisterRequest("non-e-una-mail", "corta", "Test", "Errore", LocalDate.of(1999,5,5), true, "v1.0");

        String body = mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("email: ").contains("password: ");
        assertThat(body).contains("; ");
        assertThat(body).doesNotContain("corta");
    }

    @Test
    void login_fakeEmail_throwsInvalidCredentialsException() throws Exception {
        LoginRequest loginReq = new LoginRequest("mario.arancioni@test.com", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsException() throws Exception {
        RegisterRequest req = new RegisterRequest("claudio.rossi@test.com","password123", "Mario", "Rossi" , LocalDate.of(1990, 1, 1), true, "v1.0");

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
}