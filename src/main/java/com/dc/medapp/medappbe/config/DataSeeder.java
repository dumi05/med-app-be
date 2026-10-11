package com.dc.medapp.medappbe.config;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final SpecializationRepository specializationRepository;
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
        if(userRepository.findByEmail("admin@studio-medico.it").isEmpty()) {
            userRepository.save(UserEntity.builder()
                    .email("admin@studio-medico.it")
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role(Role.ADMIN)
                    .build());
        }
    }
}
