package com.dc.medapp.medappbe.billing;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.dto.UpdatePriceRequest;
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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class PriceListControllerTest {
    private static final String BASE_PATH = "/api/v1/price-list";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private PriceListRepository priceListRepository;

    private ApiTestClient api;
    private SpecializationEntity specialization;
    private PriceListEntity priceList;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepo, passwordEncoder, doctorRepository);
        specialization = specializationRepository.save(SpecializationEntity.builder()
                .name("Cardiologia-test")
                .build());
        priceList = priceListRepository.save(PriceListEntity.builder()
                        .specialization(specialization)
                        .price(new BigDecimal("100.00"))
                        .visitType("Visita specialistica")
                .build());
        api.createUser("admin.user@test.tdc", Role.ADMIN);
        adminToken = api.login("admin.user@test.tdc");
    }

    @Test
    void admin_listsThePriceList() throws Exception {
        api.getAs(adminToken, BASE_PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.specializationName == 'Cardiologia-test')]").isNotEmpty())
                .andExpect(jsonPath("$[0].price").isNumber());
    }

    @Test
    void admin_updatesPrice() throws Exception {
        api.putAs(adminToken, BASE_PATH.concat("/").concat(priceList.getId().toString()), new UpdatePriceRequest(new BigDecimal("125.50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(priceList.getId().toString()))
                .andExpect(jsonPath("$.price").value(new BigDecimal("125.5")));
    }

    @Test
    void admin_updatingWithAPriceNotGreaterThanZero_receives400() throws Exception{
        api.putAs(adminToken, BASE_PATH.concat("/").concat(priceList.getId().toString()), new UpdatePriceRequest(BigDecimal.ZERO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void admin_updatingAnUnknownEntry_receives404() throws Exception{
        api.putAs(adminToken, BASE_PATH.concat("/999999"), new UpdatePriceRequest(BigDecimal.ONE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void doctor_cannotUserThePriceList_receives403() throws Exception{
        api.createDoctorWithConsentFlag("doc-price@list.com", false, specialization);
        var token = api.login("doc-price@list.com");

        api.getAs(token, BASE_PATH).andExpect(status().isForbidden());
        api.putAs(token, BASE_PATH.concat("/").concat(priceList.getId().toString()), new UpdatePriceRequest(BigDecimal.ONE))
                .andExpect(status().isForbidden());
    }

    @Test
    void patient_cannotUseThePriceList_receives403() throws Exception{
        var token = api.registerPatient("pat-price@list.ocm");

        api.getAs(token, BASE_PATH).andExpect(status().isForbidden());
    }

    @Test
    void anonymous_receives401() throws Exception{
        mockMvc.perform(get(BASE_PATH)).andExpect(status().isUnauthorized());
    }
}
