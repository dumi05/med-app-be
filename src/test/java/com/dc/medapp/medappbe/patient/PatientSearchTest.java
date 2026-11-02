package com.dc.medapp.medappbe.patient;

import com.dc.medapp.medappbe.audit.AuditLogRepository;
import com.dc.medapp.medappbe.audit.AuditOutcome;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.support.ApiTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class PatientSearchTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private ApiTestClient api;
    private String doctorToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        api.createUser("search.doctor@test.it", Role.DOCTOR);
        doctorToken = api.login("search.doctor@test.it");
        api.createUser("search.admin@test.it", Role.ADMIN);
        adminToken = api.login("search.admin@test.it");
    }

    private PatientEntity patient(String email, String first, String last) {
        var user = userRepository.save(UserEntity.builder().email(email).passwordHash("x").role(Role.PATIENT).build());
        return patientRepository.save(PatientEntity.builder().user(user).firstName(first).lastName(last)
                .gdprConsent(true).gdprConsentTimestamp(Instant.now()).gdprConsentVersion("v1.0").build());
    }

    private ResultActions search(String token, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/patients/search").param("query", query).header("Authorization", "Bearer " + token));
    }

    @Test
    void itFindsByAPartOfTheSurname_inAnyCase() throws Exception {
        patient("find.quar@test.it", "Mario", "Quarantotti");
        patient("find.verdi@test.it", "Luca", "Vermigli");

        search(doctorToken, "QUARANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lastName").value("Quarantotti"));
    }

    @Test
    void itFindsByAPartOfTheEmail() throws Exception {
        patient("pippo.baudo@test.it", "Giuseppe", "Baudo");

        search(adminToken, "pippo.bau").andExpect(jsonPath("$[0].email").value("pippo.baudo@test.it"));
    }

    @Test
    void itFindsBySurnameAndNameTogether_inAnyOrder() throws Exception {
        patient("two.quar1@test.it", "Mario", "Quarantotti");
        patient("two.quar2@test.it", "Anna", "Quarantotti");

        search(doctorToken, "quarantotti mario")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].firstName").value("Mario"));
        search(doctorToken, "  mario   quarant ").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void theAnswerHasOnlyWhatIsNeededToFindWhoIsCalling() throws Exception {
        patient("minimal.quar@test.it", "Mario", "Quarantotti");

        search(doctorToken, "quarantotti")
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].firstName").value("Mario"))
                .andExpect(jsonPath("$[0].email").value("minimal.quar@test.it"))
                .andExpect(jsonPath("$[0].birthDate").doesNotExist())
                .andExpect(jsonPath("$[0].userId").doesNotExist());
    }

    @Test
    void theAnonymizedPatients_areNotFound() throws Exception {
        var gone = patient("gone.quar@test.it", "Mario", "Neriggi");
        gone.setAnonymized(true);
        patientRepository.save(gone);

        search(doctorToken, "Neriggi").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void itReturnsAtMostTenPatients_sortedBySurname() throws Exception {
        for (int i = 0; i < 12; i++) {
            patient("many" + i + "@test.it", "Paolo", "Bianchini" + (char) ('A' + i));
        }

        search(doctorToken, "Bianchini")
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].lastName").value("BianchiniA"))
                .andExpect(jsonPath("$[9].lastName").value("BianchiniJ"));
    }

    @Test
    void theCharactersOfLikeAreOrdinaryCharacters() throws Exception {
        patient("like.abcd@test.it", "Mario", "Abcd");

        search(doctorToken, "a_c").andExpect(jsonPath("$.length()").value(0));
        search(doctorToken, "%%%").andExpect(jsonPath("$.length()").value(0));
        search(doctorToken, "\\\\\\").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void lessThanThreeCharacters_receives400() throws Exception {
        search(doctorToken, "ro")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        search(doctorToken, "   ").andExpect(status().isBadRequest());
        search(doctorToken, "a b").andExpect(status().isBadRequest());
    }

    @Test
    void aPatient_cannotSearch_receives403() throws Exception {
        var token = api.registerPatient("search.patient@test.it");

        search(token, "quarantotti").andExpect(status().isForbidden());
    }

    @Test
    void withoutLogin_receives401() throws Exception {
        mockMvc.perform(get("/api/v1/patients/search").param("query", "quarantotti")).andExpect(status().isUnauthorized());
    }

    @Test
    void everySearch_isInTheAccessLog_withoutTheTextThatWasSearched() throws Exception {
        search(doctorToken, "segretissimo").andExpect(status().isOk());
        search(doctorToken, "xx").andExpect(status().isBadRequest());

        var lines = auditLogRepository.findAll().stream().filter(line -> "SEARCH_PATIENTS".equals(line.getAction())).toList();
        assertThat(lines).extracting("outcome").contains(AuditOutcome.SUCCESS, AuditOutcome.FAILURE);
        assertThat(lines.toString()).doesNotContain("segretissimo");
    }
}