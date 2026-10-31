package com.dc.medapp.medappbe.doctor;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import com.dc.medapp.medappbe.doctor.dto.CreateDoctorRequest;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static com.dc.medapp.medappbe.support.ApiTestClient.PASSWORD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class DoctorControllerTest {
    public static final String MAIL = "mydoc@test.it";
    public static final String MAIL_2 = "mydoc2@test.it";
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

    private SpecializationEntity specialization;

    @BeforeEach
    void setUp() {
        specialization = specializationRepository.save(new SpecializationEntity("Cardiologia-test"));
    }

    @Test
    void getMe_Authorized() throws Exception {
        createDoctor(MAIL);
        var token = login(MAIL);
        mockMvc.perform(get("/api/v1/doctors/me").header("Authorization", "Bearer "+token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Gianpaolo"));
    }

    @Test
    void getMe_UnAuthorized() throws Exception {
        var patientToken = registerPatient(MAIL_2);
        mockMvc.perform(get("/api/v1/doctors/me").header("Authorization", "Bearer "+patientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createDoctor_asAdmin_returns200() throws Exception {
        var adminToken = createAdminAndLogin("admin.doc@test.it");
        postDoctor(adminToken, validRequest("newDoc@test.it"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newdoc@test.it"))
                .andExpect(jsonPath("$.specialization").value("Cardiologia-test"));
    }

    @Test
    void createDoctor_asPatient_return403() throws Exception {
        var patientToken = registerPatient(MAIL_2);
        postDoctor(patientToken,  validRequest("newDoc@test.it"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createDoctor_withAWeakPassword_returns400WeakPassword() throws Exception {
        var adminToken = createAdminAndLogin("admin.rules1@test.it");
        postDoctor(adminToken, new CreateDoctorRequest("weak.doc@test.it", "password1234", "Luke", "Green", specialization.getId(), true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void createDoctor_withAPasswordBuiltOnTheNameOfTheDoctor_returns400WeakPassword() throws Exception {
        var adminToken = createAdminAndLogin("admin.rules2@test.it");
        postDoctor(adminToken, new CreateDoctorRequest("name.doc@test.it", "dottor-lukegreen-2026", "Luke", "Green", specialization.getId(), true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void createDoctor_withAnEmailWithoutADotInTheDomain_returns400ValidationFailed() throws Exception {
        var adminToken = createAdminAndLogin("admin.rules3@test.it");
        postDoctor(adminToken, validRequest("luke@localhost"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createDoctor_withTheSameEmailInAnotherCase_returns400() throws Exception {
        var adminToken = createAdminAndLogin("admin.rules4@test.it");
        postDoctor(adminToken, validRequest("same.case@test.it")).andExpect(status().isOk());

        postDoctor(adminToken, validRequest("SAME.CASE@test.it"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void createDoctor_withBlankEmail_returns400() throws Exception {
        var adminToken = createAdminAndLogin("admin.user@test.it");
        postDoctor(adminToken,  validRequest(""))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createDoctor_withExistingEmail_returns400() throws Exception {
        var adminToken = createAdminAndLogin("admin.doc@test.com");
        postDoctor(adminToken, validRequest("duplicated.doc@test.ok"))
                .andExpect(status().isOk());
        postDoctor(adminToken, validRequest("duplicated.doc@test.ok"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void getAll_carriesTheSpecializationIdOfEachDoctor() throws Exception {
        createDoctor(MAIL);
        var token = registerPatient(MAIL_2);
        mockMvc.perform(get("/api/v1/doctors").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].specializationId").value(specialization.getId()))
                .andExpect(jsonPath("$[0].specialization").value("Cardiologia-test"));
    }

    private String createAdminAndLogin(String mail) throws Exception {
        var user = userRepository.save(UserEntity.builder().passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.ADMIN).email(mail).build());
        return login(user.getEmail());
    }

    private DoctorEntity createDoctor(String email) {
        var user = userRepository.save(UserEntity.builder().passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.DOCTOR).email(email).build());
        return doctorRepository.save(DoctorEntity.builder().user(user).firstName("Gianpaolo").lastName("Cortelesi").specialization(specialization).build());
    }

    private String login(String mail) throws Exception {
        var json = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(new LoginRequest(mail, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asString();
    }

    private String registerPatient(String mail) throws Exception {
        var request = new RegisterRequest(mail, PASSWORD, "Luca", "Amauri", LocalDate.of(1994,5,2), true, "v1.0");
        var json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json").content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asString();
    }

    private ResultActions postDoctor(String token, CreateDoctorRequest request) throws Exception {
        return mockMvc.perform(post("/api/v1/doctors").header("Authorization", "Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)));
    }

    private CreateDoctorRequest validRequest(String email) {
        return new CreateDoctorRequest(email, PASSWORD, "Luke", "Green", specialization.getId(), true);
    }
}
