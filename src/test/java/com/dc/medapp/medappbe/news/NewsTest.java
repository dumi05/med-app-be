package com.dc.medapp.medappbe.news;

import com.dc.medapp.medappbe.audit.AuditLogRepository;
import com.dc.medapp.medappbe.audit.AuditOutcome;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.news.dto.CreateNewsRequest;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@ActiveProfiles("test")
@Transactional
@AutoConfigureMockMvc
public class NewsTest {

    private static final String PATH = "/api/v1/news";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private SpecializationRepository specializationRepository;
    @Autowired private NewsRepository newsRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private ApiTestClient api;
    private DoctorEntity doctor;
    private DoctorEntity colleague;
    private String doctorToken;
    private String colleagueToken;
    private String adminToken;
    private String patientToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-news-test").build());
        doctor = api.createDoctorWithConsentFlag("news.doctor@test.it", false, specialization);
        colleague = api.createDoctorWithConsentFlag("news.colleague@test.it", false, specialization);
        doctorToken = api.login("news.doctor@test.it");
        colleagueToken = api.login("news.colleague@test.it");
        api.createUser("news.admin@test.it", Role.ADMIN);
        adminToken = api.login("news.admin@test.it");
        patientToken = api.registerPatient("news.patient@test.it");
    }

    private ResultActions create(String token, String title, String body, LocalDate expiresAt) throws Exception {
        return api.postAs(token, PATH, new CreateNewsRequest(title, body, expiresAt));
    }

    private Long createdId(String token, String title, LocalDate expiresAt) throws Exception {
        var json = create(token, title, "Testo delle news.", expiresAt).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    @Test
    void doctor_writesANews() throws Exception {
        create(doctorToken, "Chiusura per ferie", "Sono assente dal 10 al 14.", LocalDate.now().plusDays(10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Chiusura per ferie"))
                .andExpect(jsonPath("$.body").value("Sono assente dal 10 al 14."))
                .andExpect(jsonPath("$.authorFirstName").value("Gianpaolo"))
                .andExpect(jsonPath("$.authorLastName").value("Cortelesi"))
                .andExpect(jsonPath("$.automatic").value(false))
                .andExpect(jsonPath("$.expiresAt").value(LocalDate.now().plusDays(10).toString()));
    }

    @Test
    void theTitleAndTheText_areTrimmed() throws Exception {
        create(doctorToken, "  Avviso  ", "  Testo  ", LocalDate.now().plusDays(1))
                .andExpect(jsonPath("$.title").value("Avviso"))
                .andExpect(jsonPath("$.body").value("Testo"));
    }

    @Test
    void aPatient_theAdminAndWhoIsNotLoggedIn_cannotWrite() throws Exception {
        create(patientToken, "Avviso", "Testo", LocalDate.now().plusDays(1)).andExpect(status().isForbidden());
        create(adminToken, "Avviso", "Testo", LocalDate.now().plusDays(1)).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(PATH).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateNewsRequest("A", "B", LocalDate.now().plusDays(1)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBlankOrTooLongTitleOrText_returns400() throws Exception {
        var later = LocalDate.now().plusDays(1);
        create(doctorToken, "   ", "Testo", later).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(doctorToken, "Titolo", "   ", later).andExpect(status().isBadRequest());
        create(doctorToken, "x".repeat(120), "Testo", later).andExpect(status().isOk());
        create(doctorToken, "x".repeat(121), "Testo", later).andExpect(status().isBadRequest());
        create(doctorToken, "Titolo", "x".repeat(2000), later).andExpect(status().isOk());
        create(doctorToken, "Titolo", "x".repeat(2001), later).andExpect(status().isBadRequest());
    }

    @Test
    void theExpiryDate_isRequired_andBetweenTodayAndAYear() throws Exception {
        create(doctorToken, "Titolo", "Testo", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(doctorToken, "Titolo", "Testo", LocalDate.now().minusDays(1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        create(doctorToken, "Titolo", "Testo", LocalDate.now()).andExpect(status().isOk());
        create(doctorToken, "Titolo", "Testo", LocalDate.now().plusDays(365)).andExpect(status().isOk());
        create(doctorToken, "Titolo", "Testo", LocalDate.now().plusDays(366)).andExpect(status().isBadRequest());
    }

    @Test
    void theTextIsKeptAsText_nothingIsInterpreted() throws Exception {
        create(doctorToken, "<b>Titolo</b>", "<script>alert(1)</script>", LocalDate.now().plusDays(1))
                .andExpect(jsonPath("$.title").value("<b>Titolo</b>"))
                .andExpect(jsonPath("$.body").value("<script>alert(1)</script>"));
    }

    // ---- who reads ------------------------------------------------------------------------------

    @Test
    void thePatientsSeeTheNewsOfAllTheDoctors_theNewestFirst() throws Exception {
        createdId(doctorToken, "Prima", LocalDate.now().plusDays(5));
        createdId(colleagueToken, "Seconda", LocalDate.now().plusDays(5));

        api.getAs(patientToken, PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Seconda"))
                .andExpect(jsonPath("$[1].title").value("Prima"));
    }

    @Test
    void anExpiredNews_isNotShown_butOneThatExpiresTodayStillIs() throws Exception {
        createdId(doctorToken, "Vale ancora oggi", LocalDate.now());
        newsRepository.save(NewsEntity.builder().author(doctor).title("Scaduta ieri").body("Testo").expiresAt(LocalDate.now().minusDays(1)).build());

        api.getAs(patientToken, PATH)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Vale ancora oggi"));
    }

    @Test
    void doctorsAndTheAdmin_canReadTheNewsToo() throws Exception {
        createdId(doctorToken, "Per tutti", LocalDate.now().plusDays(5));

        api.getAs(colleagueToken, PATH).andExpect(jsonPath("$.length()").value(1));
        api.getAs(adminToken, PATH).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void withoutLogin_theNewsAreNotReadable() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH + "/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void theDoctorSeesTheirOwnNews_theExpiredOnesToo_andNotThoseOfOthers() throws Exception {
        createdId(doctorToken, "Mia", LocalDate.now().plusDays(5));
        createdId(colleagueToken, "Del collega", LocalDate.now().plusDays(5));
        newsRepository.save(NewsEntity.builder().author(doctor).title("Mia scaduta").body("Testo").expiresAt(LocalDate.now().minusDays(1)).build());

        api.getAs(doctorToken, PATH + "/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.title == 'Del collega')]").isEmpty());
        api.getAs(patientToken, PATH + "/me").andExpect(status().isForbidden());
        api.getAs(adminToken, PATH + "/me").andExpect(status().isForbidden());
    }

    // ---- delete ---------------------------------------------------------------------------------

    @Test
    void theAuthor_deletesTheirNews() throws Exception {
        var id = createdId(doctorToken, "Da togliere", LocalDate.now().plusDays(5));

        api.deleteAs(doctorToken, PATH + "/" + id).andExpect(status().isNoContent());

        api.getAs(patientToken, PATH).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theAdmin_deletesAnyNews() throws Exception {
        var id = createdId(doctorToken, "Da togliere", LocalDate.now().plusDays(5));

        api.deleteAs(adminToken, PATH + "/" + id).andExpect(status().isNoContent());
        assertThat(newsRepository.findById(id)).isEmpty();
    }

    @Test
    void anotherDoctor_cannotDelete_receives403() throws Exception {
        var id = createdId(doctorToken, "Mia", LocalDate.now().plusDays(5));

        api.deleteAs(colleagueToken, PATH + "/" + id)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
        assertThat(newsRepository.findById(id)).isPresent();
    }

    @Test
    void aPatient_cannotDelete_receives403() throws Exception {
        var id = createdId(doctorToken, "Mia", LocalDate.now().plusDays(5));

        api.deleteAs(patientToken, PATH + "/" + id).andExpect(status().isForbidden());
    }

    @Test
    void deletingAnUnknownNews_receives404() throws Exception {
        api.deleteAs(doctorToken, PATH + "/999999").andExpect(status().isNotFound());
    }

    @Test
    void anAutomaticNews_cannotBeDeletedByHand_receives400() throws Exception {
        var automatic = newsRepository.save(NewsEntity.builder().author(doctor).title("Assenza").body("Testo")
                .expiresAt(LocalDate.now().plusDays(5)).automatic(true).build());

        api.deleteAs(doctorToken, PATH + "/" + automatic.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        api.deleteAs(adminToken, PATH + "/" + automatic.getId()).andExpect(status().isBadRequest());
        assertThat(newsRepository.findById(automatic.getId())).isPresent();
        api.getAs(doctorToken, PATH + "/me").andExpect(jsonPath("$[0].automatic").value(true));
    }

    @Test
    void creatingAndDeleting_areInTheAccessLog() throws Exception {
        var id = createdId(doctorToken, "Da togliere", LocalDate.now().plusDays(5));
        api.deleteAs(colleagueToken, PATH + "/" + id).andExpect(status().isForbidden());
        api.deleteAs(doctorToken, PATH + "/" + id).andExpect(status().isNoContent());

        var lines = auditLogRepository.findAll();
        assertThat(lines).filteredOn(line -> "CREATE_NEWS".equals(line.getAction())).extracting("outcome").containsExactly(AuditOutcome.SUCCESS);
        assertThat(lines).filteredOn(line -> "DELETE_NEWS".equals(line.getAction()) && Long.valueOf(id).equals(line.getEntityId()))
                .extracting("outcome").containsExactlyInAnyOrder(AuditOutcome.FAILURE, AuditOutcome.SUCCESS);
    }

}
