package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.doctor.*;
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
public class ScheduleServiceTest {
    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private UserRepository userRepository;

    private Long doctorId;
    private Long specializationId;

    @BeforeEach
    void setUp(){
        SpecializationEntity specialization = specializationRepository.save(SpecializationEntity.builder()
                .name("Cardiologia-test")
                .build());
        this.specializationId = specialization.getId();
        UserEntity user = userRepository.save(UserEntity.builder()
                .email("mariorossi@test.it")
                .passwordHash("password")
                .role(Role.DOCTOR)
                .build());
        DoctorEntity doctor = doctorRepository.save(DoctorEntity.builder()
                .user(user)
                .firstName("Test")
                .lastName("Medico")
                .specialization(specialization)
                .build());
        this.doctorId = doctor.getId();
    }

    @Test
    public void test_slotCreation(){
        LocalDateTime now = LocalDate.now().plusDays(1).atTime(9,0);
        scheduleService.createAvailabilitySlot(doctorId, now, 30);

        var availabilities = scheduleService.searchAvailability(specializationId, now.toLocalDate());

        assertThat(availabilities).hasSize(1);
        assertThat(availabilities.get(0).isBooked()).isFalse();
    }

}
