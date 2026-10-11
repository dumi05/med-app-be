package com.dc.medapp.medappbe.doctor;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.spceialization.SpecializationEntity;
import com.dc.medapp.medappbe.spceialization.SpecializationRepository;
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
public class DoctorServiceTest {
    @Autowired
    private DoctorService doctorService;
    @Autowired
    private SpecializationRepository specializationRepository;
    private Long specializationId;

    @BeforeEach
    void setUp(){
        this.specializationId = specializationRepository.save(new SpecializationEntity("Cardiologia-test")).getId();
    }

    @Test
    public void createDoctor_createDoctorLinked(){
        DoctorEntity doctor = doctorService.createDoctor("mario.rossi@alcie.it", "password123", "Mario", "Rossi", specializationId, true);

        assertThat(doctor.getId()).isNotNull();
        assertThat(doctor.getUser()).isNotNull();
        assertThat(doctor.getUser().getRole()).isEqualTo(Role.DOCTOR);
        assertThat(doctor.getSpecialization().getId()).isEqualTo(specializationId);
        assertThat(doctor.isDelegationConsent()).isTrue();
    }
}
