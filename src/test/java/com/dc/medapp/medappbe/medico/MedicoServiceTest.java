package com.dc.medapp.medappbe.medico;

import com.dc.medapp.medappbe.auth.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class MedicoServiceTest {
    @Autowired
    private MedicoService medicoService;
    @Autowired
    private SpecializzazioneRepository specializzazioneRepository;
    private Long specializzazioneId;

    @BeforeEach
    void setUp(){
        this.specializzazioneId = specializzazioneRepository.save(new SpecializzazioneEntity("Cardiologia-test")).getId();
    }

    @Test
    public void creaMedico_creaUserEMedicoCollegati(){
        MedicoEntity medico = medicoService.creaMedico("mario.rossi@alcie.it", "password123", "Mario", "Rossi", specializzazioneId, true);

        assertThat(medico.getId()).isNotNull();
        assertThat(medico.getUser()).isNotNull();
        assertThat(medico.getUser().getRole()).isEqualTo(Role.MEDICO);
        assertThat(medico.getSpecializzazione().getId()).isEqualTo(specializzazioneId);
        assertThat(medico.isConsensoDelega()).isTrue();
    }
}
