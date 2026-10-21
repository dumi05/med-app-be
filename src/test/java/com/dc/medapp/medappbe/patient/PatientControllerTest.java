package com.dc.medapp.medappbe.patient;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import com.dc.medapp.medappbe.support.ApiTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
public class PatientControllerTest {

    private static final String PATIENT_EMAIL = "patient@gmail.com";
    public static final String ME_ANONYMIZE = "/api/v1/patients/me/anonymize";
    public static final String ME_EXPORT = "/api/v1/patients/me/export";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SpecializationRepository specializationRepository;

    private ApiTestClient api;
    private String patientToken;
    private Long patientId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        patientToken = api.registerPatient(PATIENT_EMAIL);
        patientId = patientRepository.findByUserId(
                userRepository.findByEmail(PATIENT_EMAIL).orElseThrow().getId()
        ).orElseThrow().getId();
    }

    @Test
    void patient_canExportTheirOwnData() throws Exception {
        api.getAs(patientToken, ME_EXPORT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value(PATIENT_EMAIL));
    }

    @Test
    void doctor_cannotExport_receives403() throws Exception {
        var spec= specializationRepository.save(SpecializationEntity.builder().name("Dermatologia-test").build());
        api.createDoctorWithConsentFlag("gdpr.doc@test.it", false, spec);
        api.getAs(api.login("gdpr.doc@test.it"), ME_EXPORT)
                .andExpect(status().isForbidden());
    }

    @Test
    void export_withoutToken_receives401() throws Exception {
        mockMvc.perform(get(ME_EXPORT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patient_canAnonymizeThemselves_andTheOldTokenStopsWorking() throws Exception {
        api.postAs(patientToken, ME_ANONYMIZE, null)
                .andExpect(status().isNoContent());
        api.getAs(patientToken, ME_EXPORT)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymizedPatient_cannotLogInAgain() throws Exception {
        api.postAs(patientToken, ME_ANONYMIZE, null).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON_VALUE).content(objectMapper.writeValueAsString(new LoginRequest(PATIENT_EMAIL, ApiTestClient.PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void admin_canAnonymizeAPatient() throws Exception {
        api.createUser("gdpr.admin@test.it", Role.ADMIN);
        var adminToken = api.login("gdpr.admin@test.it");

        api.postAs(adminToken, "/api/v1/patients/"+patientId+"/anonymize", null)
                .andExpect(status().isNoContent());

        assertThat(patientRepository.findById(patientId).orElseThrow().isAnonymized()).isTrue();
    }

    @Test
    void anonymizingAnUnknownPatient_receives404() throws Exception {
        api.createUser("gdpr.admin@test.it", Role.ADMIN);
        api.postAs(api.login("gdpr.admin@test.it"), "/api/v1/patients/9999/anonymize", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void anonymizingTwice_receives409() throws Exception {
        api.createUser("gdpr.admin@test.it", Role.ADMIN);
        var adminToken = api.login("gdpr.admin@test.it");
        api.postAs(adminToken, "/api/v1/patients/"+patientId+"/anonymize", null)
                .andExpect(status().isNoContent());
        api.postAs(adminToken, "/api/v1/patients/"+patientId+"/anonymize", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void patient_cannotAnonymizeAnotherPatientById_receives403() throws Exception {
        var otherToken = api.registerPatient("other.gdpr@pat.ci");
        api.postAs(otherToken, "/api/v1/patients/"+patientId+"/anonymize", null)
                .andExpect(status().isForbidden());

    }
}
