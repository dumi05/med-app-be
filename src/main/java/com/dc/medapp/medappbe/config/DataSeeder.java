package com.dc.medapp.medappbe.config;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.User;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.medico.SpecializzazioneEntity;
import com.dc.medapp.medappbe.medico.SpecializzazioneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final SpecializzazioneRepository specializzazioneRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Value("${app.admin-psw}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        if(specializzazioneRepository.count() == 0) {
            List.of("Cardiologia", "Dermatologia", "Medicina Generale", "Ortopedia")
                    .forEach(nome -> specializzazioneRepository.save(SpecializzazioneEntity.builder().nome(nome).build()));
        }
        if(userRepository.findByEmail("admin@studio-medico.it").isEmpty()) {
            userRepository.save(User.builder()
                    .email("admin@studio-medico.it")
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role(Role.ADMIN)
                    .build());
        }
    }
}
