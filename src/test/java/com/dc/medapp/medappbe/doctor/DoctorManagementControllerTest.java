package com.dc.medapp.medappbe.doctor;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.doctor.dto.UpdateDoctorRequest;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
public class DoctorManagementControllerTest {
    private static final String BASE_PATH = "/api/v1/doctors";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private SpecializationRepository specializationRepository;
    @Autowired private AvailabilitySlotRepository slotRepository;

    private ApiTestClient api;
    private SpecializationEntity cardiology;
    private SpecializationEntity dermatology;
    private DoctorEntity doctor;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        cardiology = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-test").build());
        dermatology = specializationRepository.save(SpecializationEntity.builder().name("Dermatologia-test").build());
        doctor = api.createDoctorWithConsentFlag("doc-test@mail.com", false, cardiology);
        api.createUser("admin-test@mail.cok", Role.ADMIN);
        adminToken = api.login("admin-test@mail.cok");
    }

    @Test
    void admin_updatesADoctor() throws Exception {
        api.putAs(adminToken, BASE_PATH.concat("/").concat(doctor.getId().toString()), new UpdateDoctorRequest("Gigi", "Riva", dermatology.getId(), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Gigi"))
                .andExpect(jsonPath("$.lastName").value("Riva"))
                .andExpect(jsonPath("$.specialization").value("Dermatologia-test"))
                .andExpect(jsonPath("$.delegationConsent").value(true));
    }

    @Test
    void admin_updatingWithABlankName_receives400() throws Exception {
        api.putAs(adminToken, BASE_PATH.concat("/").concat(doctor.getId().toString()), new UpdateDoctorRequest(" ", "Riva", dermatology.getId(), true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void admin_updatingAnUnknownDoctor_receives404() throws Exception {
        api.putAs(adminToken, BASE_PATH.concat("/99999"), new UpdateDoctorRequest("Anto", "Riva", dermatology.getId(), true))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_listsAllDoctorsWithDetails_deactivatedOnesIncluded() throws Exception {
        api.postAs(adminToken, BASE_PATH.concat("/").concat(doctor.getId().toString()).concat("/deactivate"), null)
                .andExpect(status().isOk());
        api.getAs(adminToken, BASE_PATH.concat("/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'doc-test@mail.com')].active").value(false));
    }

    @Test
    void admin_deactivatesAndReactivatesDoctor() throws Exception {
        api.postAs(adminToken, BASE_PATH.concat("/").concat(doctor.getId().toString()).concat("/deactivate"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        api.getAs(adminToken, BASE_PATH).andExpect(jsonPath("$[?(@.id == "+doctor.getId()+")].active").isEmpty());

        api.postAs(adminToken, BASE_PATH.concat("/").concat(doctor.getId().toString()).concat("/activate"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        api.getAs(adminToken, BASE_PATH).andExpect(jsonPath("$[?(@.id == "+doctor.getId()+")].active").isEmpty());
    }

    @Test
    void deactivating_aDoctorWithUpcomingBookedVisits_receives409() throws Exception {
        var slot = slotRepository.save(
                AvailabilitySlotEntity.builder()
                        .booked(false)
                        .durationMinutes(30)
                        .startTime(LocalDateTime.now().plusDays(5))
                        .doctor(doctor)
                        .build());
        api.book(api.registerPatient("patient.mngm@test.ok"), slot.getId());

        api.postAs(adminToken, BASE_PATH.concat("/").concat(doctor.getId().toString()).concat("/deactivate"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void deactivateDoctor_cannotLogIn_receives401() throws Exception {
        api.postAs(adminToken, BASE_PATH.concat("/").concat(doctor.getId().toString()).concat("/deactivate"), null)
                .andExpect(status().isOk());

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("doc-test@mail.com", ApiTestClient.PASSWORD)))
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void deactivatedDoctor_withATokenIssuedBefore_receives401() throws Exception {
        var doctorToken = api.login("doc-test@mail.com");
        api.getAs(doctorToken, BASE_PATH.concat("/me")).andExpect(status().isOk());
        api.postAs(adminToken, BASE_PATH.concat("/").concat(doctor.getId().toString()).concat("/deactivate"), null)
                .andExpect(status().isOk());
        api.getAs(doctorToken, BASE_PATH.concat("/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdmin_cannotManageDoctors_receives403() throws Exception {
        var doctorToken = api.login("doc-test@mail.com");
        var body = new UpdateDoctorRequest("Gigi", "Pigi", dermatology.getId(), true);

        api.putAs(doctorToken, BASE_PATH.concat("/").concat(doctor.getId().toString()), body).andExpect(status().isForbidden());
        api.postAs(doctorToken, BASE_PATH.concat("/").concat(doctor.getId().toString()).concat("/activate"), null).andExpect(status().isForbidden());
        api.postAs(doctorToken, BASE_PATH.concat("/").concat(doctor.getId().toString()).concat("/deactivate"), null).andExpect(status().isForbidden());
        api.getAs(doctorToken, BASE_PATH.concat("/admin")).andExpect(status().isForbidden());
    }


}
