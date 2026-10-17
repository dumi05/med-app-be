package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.delegations.dto.CreateDoctorDelegationRequest;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class DoctorDelegationControllerTest {

    private static final String BASE_PATH = "/api/v1/doctor-delegations";

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

    private ApiTestClient api;
    private SpecializationEntity specialization;
    private DoctorEntity delegating;
    private DoctorEntity delegate;
    private String delegatingToken;
    private String delegateToken;

    @BeforeEach
    void setUp() throws Exception {
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-dele-test").build());
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        delegating = api.createDoctorWithConsentFlag("delegating.doc@kis.lo", true, specialization);
        delegate = api.createDoctorWithConsentFlag("delegate.doc@kis.lo", true, specialization);
        delegatingToken = api.login(delegating.getUser().getEmail());
        delegateToken = api.login(delegate.getUser().getEmail());
    }

    @Test
    void doctor_canProposeADelegation() throws Exception {
        api.postAs(delegatingToken, BASE_PATH, proposal(delegate.getId(), 0, 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.delegatingDoctorId").value(delegating.getId()))
                .andExpect(jsonPath("$.delegateDoctorId").value(delegate.getId()));
    }

    @Test
    void patient_cannotProposeADelegation_receives403() throws Exception {
        var patientToken = api.registerPatient("patieng.deleg@fake.ok");
        api.postAs(patientToken, BASE_PATH, proposal(delegate.getId(), 0, 7))
                .andExpect(status().isForbidden());
    }

    @Test
    void proposingToADoctorWithoutConsent_receives400() throws Exception {
        var noConsent = api.createDoctorWithConsentFlag("noConsent.doc@mail.com", false, specialization);

        api.postAs(delegatingToken, BASE_PATH, proposal(noConsent.getId(), 0,7))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void proposingAndEndBeforeTheStart_receives400() throws Exception {
        api.postAs(delegatingToken, BASE_PATH, proposal(delegate.getId(), 2,1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void delegate_canAcceptTheDelegation() throws Exception {
        var id = propose();

        api.postAs(delegateToken, BASE_PATH.concat("/").concat(String.valueOf(id)).concat("/accept"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void delegate_canRejectTheDelegation() throws Exception {
        var id = propose();

        api.postAs(delegateToken, BASE_PATH.concat("/").concat(String.valueOf(id)).concat("/reject"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void delegatingDoctor_cannotAcceptHisOwnProposal_receives403() throws Exception {
        var id = propose();

        api.postAs(delegatingToken, BASE_PATH.concat("/").concat(String.valueOf(id)).concat("/accept"), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    void acceptingTwice_receives409() throws Exception {
        var id = propose();

        api.postAs(delegateToken, BASE_PATH.concat("/").concat(String.valueOf(id)).concat("/accept"), null)
                .andExpect(status().isOk());
        api.postAs(delegateToken, BASE_PATH.concat("/").concat(String.valueOf(id)).concat("/accept"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void me_listsTheDelegationsWhereTheDoctorIsDelegatingOrDelegate() throws Exception {
        propose();

        api.getAs(delegatingToken, BASE_PATH.concat("/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        api.getAs(delegateToken, BASE_PATH.concat("/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private CreateDoctorDelegationRequest proposal(Long delegateId, int startInDays, int endInDays) {
        return new CreateDoctorDelegationRequest(delegateId, LocalDate.now().plusDays(startInDays), LocalDate.now().plusDays(endInDays));
    }

    private long propose() throws Exception {
        var json = api.postAs(delegatingToken, BASE_PATH, proposal(delegate.getId(), 0, 7))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }
}
