package com.dc.medapp.medappbe.specialization;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.specialization.dto.CreateSpecializationRequest;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.dc.medapp.medappbe.support.ApiTestClient.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class SpecializationControllerTest {

    private static final String PATH = "/api/v1/specializations";

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
    @Autowired
    private PriceListRepository priceListRepository;

    private ApiTestClient api;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        api.createUser("admin.spec@test.it", Role.ADMIN);
        adminToken = api.login("admin.spec@test.it");
    }

    @Test
    void getAllSpecializations_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_returnsSeededSpecializations_whenAuthenticated() throws Exception {
        var token = registerPatient("pat.de@test.it");

        mockMvc.perform(get(PATH).header("Authorization", "Bearer "+token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[?(@.name == 'Cardiologia')]").isNotEmpty());
    }

    @Test
    void admin_createsASpecialization_withItsStandardPrice() throws Exception {
        var id = api.postAs(adminToken, PATH, new CreateSpecializationRequest("Neurologia", new BigDecimal("120.50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Neurologia"))
                .andReturn();
        var created = objectMapper.readTree(id.getResponse().getContentAsString()).get("id").asLong();

        var price = priceListRepository.findBySpecializationIdAndVisitType(created, BillingService.STANDARD_VISIT_TYPE);
        assertThat(price).isPresent();
        assertThat(price.get().getPrice()).isEqualByComparingTo("120.50");
        api.getAs(adminToken, PATH).andExpect(jsonPath("$[?(@.name == 'Neurologia')]").isNotEmpty());
    }

    @Test
    void admin_createsASpecialization_theNameIsTrimmed() throws Exception {
        api.postAs(adminToken, PATH, new CreateSpecializationRequest("  Oculistica  ", new BigDecimal("90.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Oculistica"));
    }

    @Test
    void admin_creatingASpecializationWithAnExistingName_returns409_evenInAnotherCase() throws Exception {
        api.postAs(adminToken, PATH, new CreateSpecializationRequest("CARDIOLOGIA", new BigDecimal("90.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPECIALIZATION_ALREADY_EXISTS"));
        assertThat(specializationRepository.findAll().stream().filter(s -> s.getName().equalsIgnoreCase("cardiologia"))).hasSize(1);
    }

    @Test
    void admin_creatingASpecializationWithAOneLetterName_returns400() throws Exception {
        api.postAs(adminToken, PATH, new CreateSpecializationRequest(" x ", new BigDecimal("90.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void admin_creatingASpecializationWithABlankNameOrANameOver80Characters_returns400() throws Exception {
        api.postAs(adminToken, PATH, new CreateSpecializationRequest("   ", new BigDecimal("90.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        api.postAs(adminToken, PATH, new CreateSpecializationRequest("x".repeat(81), new BigDecimal("90.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void admin_creatingASpecializationWithAnInvalidPrice_returns400_andSavesNothing() throws Exception {
        var before = specializationRepository.count();
        for (var price : new String[]{"0", "-5", "10000", "10.123"}) {
            api.postAs(adminToken, PATH, new CreateSpecializationRequest("Urologia", new BigDecimal(price)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        api.postAs(adminToken, PATH, new CreateSpecializationRequest("Urologia", null))
                .andExpect(status().isBadRequest());
        assertThat(specializationRepository.count()).isEqualTo(before);
    }

    @Test
    void aPatient_cannotCreateASpecialization_returns403() throws Exception {
        var token = registerPatient("pat.nospec@test.it");

        api.postAs(token, PATH, new CreateSpecializationRequest("Urologia", new BigDecimal("90.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingASpecialization_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(post(PATH).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateSpecializationRequest("Urologia", new BigDecimal("90.00")))))
                .andExpect(status().isUnauthorized());
    }

    private String registerPatient(String mail) throws Exception {
        var request = new RegisterRequest(mail, PASSWORD, "Luca", "Amauri", LocalDate.of(1994,5,2), true, "v1.0");
        var json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json").content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asString();
    }
}