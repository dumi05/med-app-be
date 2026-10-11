package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private UserEntity user;
    private SpecializationEntity specialization;
    private DoctorEntity doctor;

    @BeforeEach
    void setUp(){
        this.specialization = specializationRepository.save(SpecializationEntity.builder()
                .name("Cardiologia-test")
                .build());
        this.user = userRepository.save(UserEntity.builder()
                .email("mariorossi@test.it")
                .passwordHash("password")
                .role(Role.DOCTOR)
                .build());
        this.doctor = doctorRepository.save(DoctorEntity.builder()
                .user(user)
                .firstName("Test")
                .lastName("Medico")
                .specialization(specialization)
                .build());
    }

    @Test
    void createSlotAs_doctor_createSlotForHimself_whenDoctorIdIsOmitted() {
        var slot = scheduleService.createAvailabilitySlotAs(user, null, LocalDateTime.now(), 30);
        assertThat(slot).isNotNull();
        assertThat(slot.getDoctor().getId()).isEqualTo(doctor.getId());
    }

    @Test
    void createSlotAs_doctor_createsSlot_whenDoctorIdIsHisOwn() {
        var slot = scheduleService.createAvailabilitySlotAs(user, doctor.getId(), LocalDateTime.now(), 30);
        assertThat(slot).isNotNull();
        assertThat(slot.getDoctor().getId()).isEqualTo(doctor.getId());
    }

    @Test
    void createSlotAs_doctor_throwsUnauthorizedAccessException_whenDoctorIdIsAColleague(){
        var otherDoctor = createDoctor("pippo@test.or");
        assertThrows(UnauthorizedAccessException.class, () -> scheduleService.createAvailabilitySlotAs(user, otherDoctor.getId(), LocalDateTime.now(), 30));
    }

    @Test
    void createSlotAs_admin_createsSlotForTheGivenDoctor () {
        var admin = userRepository.save(UserEntity.builder()
                .email("admin@test.it")
                .passwordHash("password")
                .role(Role.ADMIN)
                .build());
        var slot = scheduleService.createAvailabilitySlotAs(admin, doctor.getId(), LocalDateTime.now(), 30);
        assertThat(slot).isNotNull();
        assertThat(slot.getDoctor().getId()).isEqualTo(doctor.getId());
    }

    @Test
    void createSlotAs_admin_throwsIllegalArgumentException_whenDoctorIdIsMissing() {
        var admin = userRepository.save(UserEntity.builder()
                .email("admin@test.it")
                .passwordHash("password")
                .role(Role.ADMIN)
                .build());
        assertThrows(IllegalArgumentException.class, () -> scheduleService.createAvailabilitySlotAs(admin, null, LocalDateTime.now(), 30));
    }

    @Test
    void test_slotCreationAndSearch(){
        LocalDateTime now = LocalDate.now().plusDays(1).atTime(9,0);
        scheduleService.createAvailabilitySlotAs(user, doctor.getId(), now, 30);

        var availabilities = scheduleService.searchAvailability(specialization.getId(), now.toLocalDate());

        assertThat(availabilities).hasSize(1);
        assertThat(availabilities.get(0).isBooked()).isFalse();
    }

    private DoctorEntity createDoctor(String email) {
        var user = userRepository.save(UserEntity.builder().passwordHash("PASSWORD").role(Role.DOCTOR).email(email).build());
        return doctorRepository.save(DoctorEntity.builder().user(user).firstName("Gianpaolo").lastName("Cortelesi").specialization(specialization).build());
    }
}
