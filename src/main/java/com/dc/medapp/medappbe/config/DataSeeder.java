package com.dc.medapp.medappbe.config;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Order(1)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final SpecializationRepository specializationRepository;
    private final PriceListRepository priceListRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Value("${app.admin-psw}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        if(specializationRepository.count() == 0) {
            List.of("Cardiologia", "Dermatologia", "Medicina Generale", "Ortopedia")
                    .forEach(name -> specializationRepository.save(SpecializationEntity.builder().name(name).build()));
        }
        if(userRepository.findByEmailIgnoreCase("admin@studio-medico.it").isEmpty()) {
            userRepository.save(UserEntity.builder()
                    .email("admin@studio-medico.it")
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role(Role.ADMIN)
                    .mustChangePassword(true)
                    .build());
        }
        if (priceListRepository.count() == 0) {
            specializationRepository.findAll().forEach(specialization ->
                    priceListRepository.save(PriceListEntity.builder()
                            .specialization(specialization)
                            .visitType(BillingService.STANDARD_VISIT_TYPE)
                            .price(new BigDecimal("100.00"))
                            .build()));
        }
    }
}
