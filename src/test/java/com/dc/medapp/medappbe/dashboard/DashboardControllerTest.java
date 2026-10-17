package com.dc.medapp.medappbe.dashboard;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
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
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class DashboardControllerTest {
    private static final String BASE_PATH = "/api/v1/dashboard/kpi";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private SpecializationRepository specializationRepository;

    private ApiTestClient api;
    private SpecializationEntity specialization;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Dermatologia-test").build());
    }

    @Test
    void admin_getsKpi() throws Exception {
        api.createUser("admin.dashoard@test.it", Role.ADMIN);
        var token = api.login("admin.dashoard@test.it");

        api.getAs(token, BASE_PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAppointments").value(0))
                .andExpect(jsonPath("$.appointmentsByStatus.BOOKED").value(0))
                .andExpect(jsonPath("$.totalRevenue").value(0))
                .andExpect(jsonPath("$.revenueBySpecialization").isArray());
    }

    @Test
    void doctor_cannotGetKpis_receives403() throws Exception {
        api.createDoctorWithConsentFlag("doc-dashboard@test.it", false, specialization);
        var token = api.login("doc-dashboard@test.it");
        api.getAs(token, BASE_PATH)
                .andExpect(status().isForbidden());
    }

    @Test
    void patient_cannotGetKpis_receives403() throws Exception {
        api.registerPatient("patient@test.it");
        var token = api.login("patient@test.it");
        api.getAs(token, BASE_PATH).andExpect(status().isForbidden());
    }

    @Test
    void anonymous_receives401() throws Exception {
        mockMvc.perform(get(BASE_PATH)).andExpect(status().isUnauthorized());
    }
}
