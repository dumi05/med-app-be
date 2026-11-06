package com.dc.medapp.medappbe.doctor;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
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

import static com.dc.medapp.medappbe.support.ApiTestClient.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
public class DoctorForcePasswordChangeTest {

    private static final String ADMIN_EMAIL = "admin.force.doctor@test.it";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SpecializationRepository specializationRepository;

    private ApiTestClient api;
    private String adminToken;
    private DoctorEntity doctor;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        api.createUser(ADMIN_EMAIL, Role.ADMIN);
        adminToken = api.login(ADMIN_EMAIL);
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Force-test").build());
        doctor = api.createDoctorWithConsentFlag("force.doctor@test.it", false, specialization);
    }

    private String path(Long id) {
        return "/api/v1/doctors/" + id + "/force-password-change";
    }

    @Test
    void admin_forcesTheChange_andTheAnswerShowsIt() throws Exception {
        var user = doctor.getUser();
        var versionBefore = user.getTokenVersion();

        api.postAs(adminToken, path(doctor.getId()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(doctor.getId()))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(versionBefore + 1);
    }

    @Test
    void theAdminListShowsWhoHasAPendingChange() throws Exception {
        api.getAs(adminToken, "/api/v1/doctors/admin")
                .andExpect(jsonPath("$[?(@.id == " + doctor.getId() + ")].mustChangePassword").value(false));

        api.postAs(adminToken, path(doctor.getId()), null).andExpect(status().isOk());

        api.getAs(adminToken, "/api/v1/doctors/admin")
                .andExpect(jsonPath("$[?(@.id == " + doctor.getId() + ")].mustChangePassword").value(true));
    }

    @Test
    void afterTheForcedChange_theOldTokenStopsWorking_andTheSamePasswordLogsInOnlyToChangeIt() throws Exception {
        var oldToken = api.login("force.doctor@test.it");
        api.getAs(oldToken, "/api/v1/doctors/me").andExpect(status().isOk());

        api.postAs(adminToken, path(doctor.getId()), null).andExpect(status().isOk());

        api.getAs(oldToken, "/api/v1/doctors/me").andExpect(status().isUnauthorized());
        var login = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("force.doctor@test.it", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        api.getAs(objectMapper.readTree(login).get("token").asString(), "/api/v1/doctors/me")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void forcingAnUnknownDoctor_returns404() throws Exception {
        api.postAs(adminToken, path(999999L), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void aPatientAndADoctor_cannotForceTheChange() throws Exception {
        var patientToken = api.registerPatient("force.doctor.nope@test.it");

        api.postAs(patientToken, path(doctor.getId()), null).andExpect(status().isForbidden());
        api.postAs(api.login("force.doctor@test.it"), path(doctor.getId()), null).andExpect(status().isForbidden());
        mockMvc.perform(post(path(doctor.getId()))).andExpect(status().isUnauthorized());
    }

    @Test
    void everyForcedChange_isInTheAccessLog_withTheIdOfTheDoctor() throws Exception {
        api.postAs(adminToken, path(doctor.getId()), null).andExpect(status().isOk());
        api.postAs(adminToken, path(999999L), null).andExpect(status().isNotFound());

        api.getAs(adminToken, "/api/v1/audit-logs?limit=200")
                .andExpect(jsonPath("$[?(@.action == 'FORCE_PASSWORD_CHANGE' && @.entityName == 'Doctor' && @.entityId == " + doctor.getId() + " && @.outcome == 'SUCCESS')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action == 'FORCE_PASSWORD_CHANGE' && @.entityName == 'Doctor' && @.entityId == 999999 && @.outcome == 'FAILURE')]").isNotEmpty());
    }
}