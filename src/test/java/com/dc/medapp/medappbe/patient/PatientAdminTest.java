package com.dc.medapp.medappbe.patient;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static com.dc.medapp.medappbe.support.ApiTestClient.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
public class PatientAdminTest {

    private static final String PATH = "/api/v1/patients";
    private static final String ADMIN_EMAIL = "admin.patients@test.it";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SpecializationRepository specializationRepository;

    private ApiTestClient api;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        api.createUser(ADMIN_EMAIL, Role.ADMIN);
        adminToken = api.login(ADMIN_EMAIL);
    }

    private PatientEntity patient(String email, String firstName, String lastName) {
        var user = userRepository.save(UserEntity.builder().email(email).role(Role.PATIENT)
                .passwordHash(passwordEncoder.encode(PASSWORD)).build());
        return patientRepository.save(PatientEntity.builder().user(user).firstName(firstName).lastName(lastName)
                .gdprConsent(true).gdprConsentTimestamp(Instant.now()).gdprConsentVersion("v1.0").build());
    }

    private JsonNode listed() throws Exception {
        var json = api.getAs(adminToken, PATH).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private int positionOf(JsonNode list, String email) {
        for (int i = 0; i < list.size(); i++) {
            if (email.equals(list.get(i).get("email").asString())) {
                return i;
            }
        }
        return -1;
    }

    private Long idOfRegistered(String email) {
        return patientRepository.findByUserId(userRepository.findByEmailIgnoreCase(email).orElseThrow().getId()).orElseThrow().getId();
    }

    private String doctorToken(String email) throws Exception {
        var spec = specializationRepository.save(SpecializationEntity.builder().name("Specialization-" + email).build());
        api.createDoctorWithConsentFlag(email, false, spec);
        return api.login(email);
    }

    // ---- the list

    @Test
    void admin_listsThePatients_withTheirDataAndNothingClinical() throws Exception {
        var ada = patient("ada.list@test.it", "Ada", "Lovelace");

        var list = listed();
        var row = list.get(positionOf(list, "ada.list@test.it"));

        assertThat(row.get("id").asLong()).isEqualTo(ada.getId());
        assertThat(row.get("firstName").asString()).isEqualTo("Ada");
        assertThat(row.get("lastName").asString()).isEqualTo("Lovelace");
        assertThat(row.get("registeredAt").isNull()).isFalse();
        assertThat(row.get("mustChangePassword").asBoolean()).isFalse();
        assertThat(row.has("birthDate")).isFalse();
        assertThat(row.has("passwordHash")).isFalse();
    }

    @Test
    void thePatients_areSortedByLastNameThenByFirstName() throws Exception {
        patient("sort.verdi.marco@test.it", "Marco", "Verdi-sort");
        patient("sort.verdi.anna@test.it", "Anna", "Verdi-sort");
        patient("sort.bianchi@test.it", "Zoe", "Bianchi-sort");

        var list = listed();

        assertThat(positionOf(list, "sort.bianchi@test.it")).isLessThan(positionOf(list, "sort.verdi.anna@test.it"));
        assertThat(positionOf(list, "sort.verdi.anna@test.it")).isLessThan(positionOf(list, "sort.verdi.marco@test.it"));
    }

    @Test
    void anAnonymizedPatient_isNotListed() throws Exception {
        var gone = patient("gone.list@test.it", "Paziente", "Anonimizzato");
        gone.setAnonymized(true);
        patientRepository.save(gone);

        assertThat(positionOf(listed(), "gone.list@test.it")).isEqualTo(-1);
    }

    @Test
    void aPatientAndADoctor_cannotListThePatients() throws Exception {
        var patientToken = api.registerPatient("list.nope@test.it");

        api.getAs(patientToken, PATH).andExpect(status().isForbidden());
        api.getAs(doctorToken("list.doc@test.it"), PATH).andExpect(status().isForbidden());
    }

    @Test
    void listingWithoutAToken_returns401() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    // ---- the forced change of the password

    @Test
    void admin_forcesThePasswordChange_andTheAnswerShowsIt() throws Exception {
        var patient = patient("force.one@test.it", "Carla", "Neri");
        var user = patient.getUser();
        var versionBefore = user.getTokenVersion();

        api.postAs(adminToken, PATH + "/" + patient.getId() + "/force-password-change", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.getId()))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(versionBefore + 1);
        assertThat(user.getPasswordHash()).isNotBlank();
    }

    @Test
    void afterTheForcedChange_theOldTokenStopsWorking() throws Exception {
        var patientToken = api.registerPatient("force.token@test.it");
        api.getAs(patientToken, PATH + "/me/export").andExpect(status().isOk());

        api.postAs(adminToken, PATH + "/" + idOfRegistered("force.token@test.it") + "/force-password-change", null)
                .andExpect(status().isOk());

        api.getAs(patientToken, PATH + "/me/export").andExpect(status().isUnauthorized());
    }

    @Test
    void afterTheForcedChange_theSamePasswordStillLogsIn_butOnlyToChangeIt() throws Exception {
        api.registerPatient("force.login@test.it");
        api.postAs(adminToken, PATH + "/" + idOfRegistered("force.login@test.it") + "/force-password-change", null)
                .andExpect(status().isOk());

        var login = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("force.login@test.it", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        var token = objectMapper.readTree(login).get("token").asString();

        api.getAs(token, PATH + "/me/export")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void afterTheNewPassword_thePatientWorksAgain_andTheListNoLongerShowsThePendingChange() throws Exception {
        api.registerPatient("force.done@test.it");
        api.postAs(adminToken, PATH + "/" + idOfRegistered("force.done@test.it") + "/force-password-change", null)
                .andExpect(status().isOk());
        var list = listed();
        assertThat(list.get(positionOf(list, "force.done@test.it")).get("mustChangePassword").asBoolean()).isTrue();

        var token = api.firstLogin("force.done@test.it", PASSWORD);

        api.getAs(token, PATH + "/me/export").andExpect(status().isOk());
        var after = listed();
        assertThat(after.get(positionOf(after, "force.done@test.it")).get("mustChangePassword").asBoolean()).isFalse();
    }

    @Test
    void forcingAnUnknownPatient_returns404() throws Exception {
        api.postAs(adminToken, PATH + "/999999/force-password-change", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void forcingAnAnonymizedPatient_returns404_andChangesNothing() throws Exception {
        var gone = patient("gone.force@test.it", "Paziente", "Anonimizzato");
        gone.setAnonymized(true);
        patientRepository.save(gone);

        api.postAs(adminToken, PATH + "/" + gone.getId() + "/force-password-change", null)
                .andExpect(status().isNotFound());

        assertThat(gone.getUser().isMustChangePassword()).isFalse();
    }

    @Test
    void aPatientAndADoctor_cannotForceTheChange() throws Exception {
        var patientToken = api.registerPatient("force.nope@test.it");
        var id = idOfRegistered("force.nope@test.it");

        api.postAs(patientToken, PATH + "/" + id + "/force-password-change", null).andExpect(status().isForbidden());
        api.postAs(doctorToken("force.doc@test.it"), PATH + "/" + id + "/force-password-change", null).andExpect(status().isForbidden());
    }

    @Test
    void forcingWithoutAToken_returns401() throws Exception {
        mockMvc.perform(post(PATH + "/1/force-password-change")).andExpect(status().isUnauthorized());
    }

    @Test
    void everyForcedChange_isInTheAccessLog_withTheIdOfThePatient() throws Exception {
        api.registerPatient("force.log@test.it");
        var id = idOfRegistered("force.log@test.it");

        api.postAs(adminToken, PATH + "/" + id + "/force-password-change", null).andExpect(status().isOk());
        api.postAs(adminToken, PATH + "/999999/force-password-change", null).andExpect(status().isNotFound());

        api.getAs(adminToken, "/api/v1/audit-logs?limit=200")
                .andExpect(jsonPath("$[?(@.action == 'FORCE_PASSWORD_CHANGE' && @.entityName == 'Patient' && @.entityId == " + id + " && @.outcome == 'SUCCESS' && @.userEmail == '" + ADMIN_EMAIL + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action == 'FORCE_PASSWORD_CHANGE' && @.entityId == 999999 && @.outcome == 'FAILURE')]").isNotEmpty());
    }
}