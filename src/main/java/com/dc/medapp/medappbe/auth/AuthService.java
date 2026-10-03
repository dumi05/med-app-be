package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.auth.dto.AuthResponse;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import com.dc.medapp.medappbe.paziente.Paziente;
import com.dc.medapp.medappbe.paziente.PazienteRepository;
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
    private final PazienteRepository pazienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register (RegisterRequest request) {

        if(!request.consensoGdpr()) {
            throw new IllegalArgumentException("Il consenso GDPR è obbligatorio per la registrazione.");
        }

        if(userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("L'email è già in uso.");
        }

        var user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.PAZIENTE)
                .build();

        userRepository.save(user);
        var paziente = Paziente.builder()
                .user(user)
                .nome(request.nome())
                .cognome(request.cognome())
                .dataNascita(request.dataNascita())
                .consensoGdpr(true)
                .consensoGdprVersion(request.consensoGdprVersion())
                .consensoGdprTimestamp(Instant.now())
                .anonimizzato(false)
                .build();

        pazienteRepository.save(paziente);

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole());
    }

    public AuthResponse login (LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Utente non registrato con la mail: " + request.email()));

        if(!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // todo: aggiungere counter per quante volte l'utente ha sbagliato la password e bloccare l'account dopo un certo numero di tentativi
            throw new IllegalArgumentException("Password non valida.");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole());
    }
}
