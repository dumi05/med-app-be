package com.dc.medapp.medappbe.billing;

import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class PriceListServiceTest {
    @Autowired
    private PriceListService service;
    @Autowired
    private PriceListRepository priceListRepo;
    @Autowired
    private SpecializationRepository specializationRepo;

    private SpecializationEntity specialization;

    @BeforeEach
    void setUp() {
        specialization = specializationRepo.save(
                SpecializationEntity.builder()
                        .name("Cardiologia-pricelist-test")
                        .build());
    }

    @Test
    void findAll_returnsEntriesOrderedBySpecializationName() {
        var anotherSpecialization = specializationRepo.save(
                SpecializationEntity.builder()
                        .name("Dermatologia-pricelist-test").build());
        savePrice(anotherSpecialization, "Visita specialistica", "80.00");
        savePrice(specialization, "Visita specialistica", "100.00");

        var names = service.findAll().stream().map(e -> e.getSpecialization().getName()).toList();
        assertThat(names).containsSubsequence("Cardiologia-pricelist-test", "Dermatologia-pricelist-test");
    }

    @Test
    void updatePrice_changesThePrice() {
        var price1 = savePrice(specialization, "Visita specialistica", "100.00");
        var updated = service.updatePrice(price1.getId(), new BigDecimal("119.99"));

        assertThat(updated.getPrice()).isEqualByComparingTo("119.99");
        assertThat(priceListRepo.findById(price1.getId()).orElseThrow().getPrice()).isEqualByComparingTo("119.99");
    }

    @Test
    void updatePrice_throwsResourceNotFoundException_whenThePriceIdDoesNotExist() {
        assertThrows(ResourceNotFoundException.class, () -> service.updatePrice(999_999L, new BigDecimal("10.00")));
    }

    @Test
    void priceList_allowsTwoVisitTypesForTheSameSpecialization() {
        savePrice(specialization, "Visita specialistica", "100.00");
        savePrice(specialization, "Visita di controllo", "60.00");

        assertThat(priceListRepo.findAll().stream()
                .filter(x -> x.getSpecialization().getId().equals(specialization.getId())))
                .hasSize(2);
    }

    private PriceListEntity savePrice(SpecializationEntity specialization, String visitType, String price){
        return priceListRepo.save(PriceListEntity.builder()
                .price(new BigDecimal(price))
                .specialization(specialization)
                .visitType(visitType)
                .build());
    }
}
