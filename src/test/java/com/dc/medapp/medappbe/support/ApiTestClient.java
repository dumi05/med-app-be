package com.dc.medapp.medappbe.support;

import com.dc.medapp.medappbe.appointments.dto.CreateAppointmentRequest;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ApiTestClient {

    public static final String PASSWORD = "password123";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;

    public ApiTestClient(MockMvc mockMvc, ObjectMapper objectMapper, UserRepository userRepository, PasswordEncoder passwordEncoder, DoctorRepository doctorRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.doctorRepository = doctorRepository;
    }

    public UserEntity createUser(String mail, Role role) {
        return userRepository.save(UserEntity.builder().role(role).passwordHash(passwordEncoder.encode(PASSWORD)).email(mail).build());
    }

    public DoctorEntity createDoctorWithConsentFlag(String mail, boolean consent, SpecializationEntity specialization){
        var user = createUser(mail, Role.DOCTOR);
        return doctorRepository.save(DoctorEntity.builder().user(user).specialization(specialization).firstName("Gianpaolo").lastName("Cortelesi").delegationConsent(consent).build());
    }

    public String registerPatient(String email) throws Exception {
        var request = new RegisterRequest(email, PASSWORD, "Luca", "Amauri", LocalDate.of(1994, 5, 2), true, "v1.0");
        var json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json").content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asString();
    }

    public String login(String email) throws Exception {
        var json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asString();
    }

    public long book(String token, Long slotId) throws Exception {
        var json = postAs(token, "/api/v1/appointments", new CreateAppointmentRequest(slotId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    public ResultActions postAs(String token, String path, Object body) throws Exception {
        var request = post(path).header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType("application/json").content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    public ResultActions getAs(String token, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    public ResultActions putAs(String token, String path, Object body) throws Exception {
        return mockMvc.perform(
                put(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body))
        );
    }
}
