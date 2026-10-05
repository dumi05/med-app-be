package com.dc.medapp.medappbe.medico;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.User;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.common.exception.RisorsaNonTrovataException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MedicoService {
    private final MedicoRepository medicoRepository;
    private final SpecializzazioneRepository specializzazioneRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MedicoEntity creaMedico(String email, String password, String nome, String cognome, Long specializzazioneId, boolean consensoDelega) {
        var specializzazione = specializzazioneRepository.findById(specializzazioneId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Specializzazione non trovata: " + specializzazioneId));

        if(userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email già in uso: " + email);
        }
        var user = userRepository.save(User.builder().email(email).passwordHash(passwordEncoder.encode(password)).role(Role.MEDICO).build());

        var medico = MedicoEntity.builder().user(user).nome(nome).cognome(cognome).specializzazione(specializzazione).consensoDelega(consensoDelega).build();

        return medicoRepository.save(medico);
    }

    @Transactional(readOnly = true)
    public List<MedicoEntity> findAllMedico() {
        return medicoRepository.findAll();
    }
}
