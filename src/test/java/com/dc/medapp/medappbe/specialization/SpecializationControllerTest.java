package com.dc.medapp.medappbe.specialization;

import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class SpecializationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllSpecializations_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/specializations")).andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_returnsSeededSpecializations_whenAuthenticated() throws Exception {
        var token = registerPatient("pat.de@test.it");

        mockMvc.perform(get("/api/v1/specializations").header("Authorization", "Bearer "+token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[?(@.name == 'Cardiologia')]").isNotEmpty());
    }

    private String registerPatient(String mail) throws Exception {
        var request = new RegisterRequest(mail, "myPassword26!", "Luca", "Amauri", LocalDate.of(1994,5,2), true, "v1.0");
        var json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json").content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asString();
    }
}
