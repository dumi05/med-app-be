package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.auth.dto.AuthResponse;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import com.dc.medapp.medappbe.common.exception.InvalidCredentialsException;
import com.dc.medapp.medappbe.patient.PatientEntity;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register (RegisterRequest request) {

        if(!request.gdprConsent()) {
            throw new IllegalArgumentException("Il consenso GDPR è obbligatorio per la registrazione.");
        }

        if(userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("L'email è già in uso.");
        }

        var user = UserEntity.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.PATIENT)
                .build();

        userRepository.save(user);
        var patient = PatientEntity.builder()
                .user(user)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .birthDate(request.birthDate())
                .gdprConsent(true)
                .gdprConsentVersion(request.gdprConsentVersion())
                .gdprConsentTimestamp(Instant.now())
                .anonymized(false)
                .build();

        patientRepository.save(patient);

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole());
    }

    public AuthResponse login (LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Credenziali non valide"));

        if(!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // todo: aggiungere counter per quante volte l'utente ha sbagliato la password e bloccare l'account dopo un certo numero di tentativi
            throw new InvalidCredentialsException("Credenziali non valide");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole());
    }
}
