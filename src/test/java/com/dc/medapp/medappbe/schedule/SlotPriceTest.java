package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class SlotPriceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SpecializationRepository specializationRepository;
    @Autowired private PriceListRepository priceListRepository;
    @Autowired private AvailabilitySlotRepository slotRepository;

    private ApiTestClient api;
    private String patientToken;
    private final LocalDate day = LocalDate.now().plusDays(3);

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        patientToken = api.registerPatient("slot.price@patient.ex");
    }

    private SpecializationEntity specializationWithPrice(String name, String price) {
        var specialization = specializationRepository.save(new SpecializationEntity(name));
        if(price != null){
            priceListRepository.save(PriceListEntity.builder().specialization(specialization).price(new BigDecimal(price)).visitType(BillingService.STANDARD_VISIT_TYPE).build());
        }
        var doc = api.createDoctorWithConsentFlag(name.toLowerCase(Locale.ROOT)+"@test.it", false, specialization);
        slotRepository.save(AvailabilitySlotEntity.builder().doctor(doc).startTime(day.atTime(9,0)).durationMinutes(30).booked(false).build());
        return specialization;
    }

    private ResultActions search(SpecializationEntity spec) throws Exception {
        return api.getAs(patientToken, "/api/v1/schedule/availability?specializationId="+spec.getId()+"&from="+day+"&to="+day);
    }

    @Test
    void theFreeSlots_carryThePriceOfTheSpecialization() throws Exception {
        search(specializationWithPrice("Cardiologia-p-test", "120.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(120.00));
    }

    @Test
    void aSlotWithASpecializationWithoutPrice_haveNoPrice_butAreStillListed() throws Exception {
        search(specializationWithPrice("Dermatologia-p-test", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").isEmpty());
    }

    @Test
    void anotherPriceOfTheSameSpecialization_isNotTheStandardOne() throws Exception {
        var spec = specializationWithPrice("Oculistica-p-test", "100.00");
        priceListRepository.save(PriceListEntity.builder().specialization(spec).visitType("Controllo").price(new BigDecimal("120.00")).build());
        search(spec)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(100.00));
    }

    @Test
    void theNewPrice_countsAtOnce() throws Exception {
        var spec = specializationWithPrice("Oculistica-p-test", "100.00");
        var entry = priceListRepository.findBySpecializationIdAndVisitType(spec.getId(), BillingService.STANDARD_VISIT_TYPE).orElseThrow();
        entry.setPrice(new BigDecimal("150.00"));
        priceListRepository.save(entry);
        search(spec)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(150.00));
    }
}
