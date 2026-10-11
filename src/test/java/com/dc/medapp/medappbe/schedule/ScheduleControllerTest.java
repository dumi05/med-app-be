package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.schedule.dto.SlotRequest;
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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ScheduleControllerTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MockMvc mockMvc;
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
    void setUp() throws Exception {
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-test").build());
    }

    @Test
    void slotCreationAs_loggedDoctor_withoutDoctorId() throws Exception {
        var doctor = createDoctor("doc@email.ot");
        var token = login("doc@email.ot");
        var request = new SlotRequest(null, LocalDateTime.now().plusDays(51), 30);
        mockMvc.perform(post("/api/v1/schedule/slot").header("Authorization", "Bearer " + token).contentType("application/json").content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(doctor.getId()));
    }

    @Test
    void slotCreationAs_loggedDoctor_forAColleague_throwsUnauthorizedAccessException() throws Exception {
        var doctor = createDoctor("doc@email.ot");
        var otherDoctor = createDoctor("doc2@email.ot");
        var token = login("doc@email.ot");
        var request = new SlotRequest(otherDoctor.getId(), LocalDateTime.now().plusDays(45), 30);
        mockMvc.perform(post("/api/v1/schedule/slot").header("Authorization", "Bearer " + token).contentType("application/json").content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
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
    private DoctorEntity createDoctor(String email) {
        var user = userRepository.save(UserEntity.builder().passwordHash(passwordEncoder.encode("PASSWORD")).role(Role.DOCTOR).email(email).build());
        return doctorRepository.save(DoctorEntity.builder().user(user).firstName("Gianpaolo").lastName("Cortelesi").specialization(specialization).build());
    }
}


