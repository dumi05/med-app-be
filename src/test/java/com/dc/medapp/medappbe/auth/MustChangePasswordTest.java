package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.auth.dto.ChangePasswordRequest;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.doctor.dto.CreateDoctorRequest;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Map;

import static com.dc.medapp.medappbe.support.ApiTestClient.PASSWORD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class MustChangePasswordTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SpecializationRepository specializationRepository;

    private ApiTestClient api;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
    }

    private String flaggedToken(String email, Role role) throws Exception {
        userRepository.save(UserEntity.builder().email(email).role(role).passwordHash(passwordEncoder.encode(PASSWORD)).mustChangePassword(true).build());
        return api.login(email);
    }

    private JsonNode json(String content) {
        return objectMapper.readTree(content);
    }

    @Test
    void theLoginTellsThatThePasswordMustBeChanged() throws Exception {
        userRepository.save(UserEntity.builder().email("must.login@test.it").passwordHash(passwordEncoder.encode(PASSWORD))
                .mustChangePassword(true).role(Role.ADMIN).build());
        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content(objectMapper.writeValueAsString(new LoginRequest("must.login@test.it", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void aFlaggedAccountCanDoNothingButchangeThePassword() throws Exception {
        var token = flaggedToken("must.login@test.it", Role.ADMIN);

        api.getAs(token, "/api/v1/doctors")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        api.getAs(token, "/api/v1/dashboard/kpi")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void theblockAppliesToTheWritingsToo() throws Exception {
        var token = flaggedToken("must.login.doc@test.it", Role.DOCTOR);

        api.postAs(token, "/api/v1/schedule/slot", Map.of("startTime","2030-01-01T10:00:00", "durationMinutes", "30"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void aFlaggedAccountCanStillAskWhoItIs() throws Exception {
        var token = flaggedToken("must.me@test.it", Role.ADMIN);

        api.getAs(token, "/api/v1/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void changingThePassword_clearsTheFlag_andTheNewTokenWorks() throws Exception {
        var token = flaggedToken("must.change@test.it", Role.ADMIN);

        var changed = api.postAs(token, "/api/v1/auth/change-password",
                        new ChangePasswordRequest(ApiTestClient.PASSWORD, ApiTestClient.CHANGED_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();

        api.getAs(json(changed).get("token").asString(), "/api/v1/doctors").andExpect(status().isOk());
    }

    @Test
    void aDoctorCreatedByTheAdminMustChangeThePassword() throws Exception {
        api.createUser("must.creator@test.it", Role.ADMIN);
        var adminToken = api.login("must.creator@test.it");
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-must-test").build());

        api.postAs(adminToken, "/api/v1/doctors",
                        new CreateDoctorRequest("must.newdoc@test.it", ApiTestClient.PASSWORD, "Luke", "Green", specialization.getId(), true))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("must.newdoc@test.it", ApiTestClient.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void aPatientWhoRegistersChoseThePasswordHimself_soNothingIsRequired() throws Exception {
        var request = new RegisterRequest("must.patient@test.it", ApiTestClient.PASSWORD, "Luca", "Amauri", LocalDate.of(1994, 5, 2), true, "v1.0");

        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void anAccountWithoutTheFlagIsNotBlocked() throws Exception {
        api.createUser("must.free@test.it", Role.ADMIN);

        api.getAs(api.login("must.free@test.it"), "/api/v1/doctors").andExpect(status().isOk());
    }
}
