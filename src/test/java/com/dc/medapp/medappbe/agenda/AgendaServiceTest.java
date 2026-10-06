package com.dc.medapp.medappbe.agenda;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.User;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.medico.MedicoEntity;
import com.dc.medapp.medappbe.medico.MedicoRepository;
import com.dc.medapp.medappbe.medico.SpecializzazioneEntity;
import com.dc.medapp.medappbe.medico.SpecializzazioneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class AgendaServiceTest {
    @Autowired
    private AgendaService agendaService;
    @Autowired
    private MedicoRepository medicoRepository;
    @Autowired
    private SpecializzazioneRepository specializzazioneRepository;
    @Autowired
    private UserRepository userRepository;

    private Long medicoId;
    private Long specializzazioneId;

    @BeforeEach
    void setUp(){
        SpecializzazioneEntity specializzazione = specializzazioneRepository.save(SpecializzazioneEntity.builder()
                .nome("Cardiologia-test")
                .build());
        this.specializzazioneId = specializzazione.getId();
        User user = userRepository.save(User.builder()
                .email("mariorossi@test.it")
                .passwordHash("password")
                .role(Role.MEDICO)
                .build());
        MedicoEntity medico = medicoRepository.save(MedicoEntity.builder()
                .user(user)
                .nome("Test")
                .cognome("Medico")
                .specializzazione(specializzazione)
                .build());
        this.medicoId = medico.getId();
    }

    @Test
    public void test_creazioneSlot(){
        LocalDateTime now = LocalDate.now().plusDays(1).atTime(9,0);
        agendaService.createSlotDisponibilita(medicoId, now, 30);

        var disponibili = agendaService.cercaDisponibilita(specializzazioneId, now.toLocalDate());

        assertThat(disponibili).hasSize(1);
        assertThat(disponibili.get(0).isOccupato()).isFalse();
    }

}
