package com.dc.medapp.medappbe.doctor;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
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

import java.time.LocalDate;

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

    private DoctorEntity createDoctor(String email) {
        var user = userRepository.save(UserEntity.builder().passwordHash(passwordEncoder.encode("PASSWORD")).role(Role.DOCTOR).email(email).build());
        return doctorRepository.save(DoctorEntity.builder().user(user).firstName("Gianpaolo").lastName("Cortelesi").specialization(specialization).build());
    }

    private String login(String mail) throws Exception {
        var json = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(new LoginRequest(mail, "PASSWORD"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asString();
    }

    private String registerPatient(String mail) throws Exception {
        var request = new RegisterRequest(mail, "PASSWORD", "Luca", "Amauri", LocalDate.of(1994,5,2), true, "v1.0");
        var json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json").content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asString();
    }
}
