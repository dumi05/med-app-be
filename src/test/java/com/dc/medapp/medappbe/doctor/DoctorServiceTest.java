package com.dc.medapp.medappbe.doctor;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import com.dc.medapp.medappbe.support.ApiTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static com.dc.medapp.medappbe.support.ApiTestClient.PASSWORD;
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
    public void createDoctor_throwsWeakPasswordException_whenThePasswordIsWeak() {
        org.junit.jupiter.api.Assertions.assertThrows(com.dc.medapp.medappbe.common.exception.WeakPasswordException.class,
                () -> doctorService.createDoctor("weak.doctor@alcie.it", "password1234", "Mario", "Rossi", specializationId, true));
    }

    @Test
    public void createDoctor_storesTheEmailInLowerCase() {
        DoctorEntity doctor = doctorService.createDoctor("  Upper.Case@Alcie.IT ", ApiTestClient.PASSWORD, "Mario", "Rossi", specializationId, true);

        org.assertj.core.api.Assertions.assertThat(doctor.getUser().getEmail()).isEqualTo("upper.case@alcie.it");
    }

    @Test
    public void createDoctor_createDoctorLinked(){
        DoctorEntity doctor = doctorService.createDoctor("mario.rossi@alcie.it", PASSWORD, "Mario", "Rossi", specializationId, true);

        assertThat(doctor.getId()).isNotNull();
        assertThat(doctor.getUser()).isNotNull();
        assertThat(doctor.getUser().getRole()).isEqualTo(Role.DOCTOR);
        assertThat(doctor.getSpecialization().getId()).isEqualTo(specializationId);
        assertThat(doctor.isDelegationConsent()).isTrue();
    }
}
