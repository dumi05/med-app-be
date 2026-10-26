package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.common.exception.SlotOverlapException;
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
import java.time.LocalTime;

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
    @Autowired
    private AvailabilitySlotRepository slotRepository;

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
        var slot = scheduleService.createAvailabilitySlotAs(user, null, LocalDateTime.now().plusDays(1), 30);
        assertThat(slot).isNotNull();
        assertThat(slot.getDoctor().getId()).isEqualTo(doctor.getId());
    }

    @Test
    void createSlotAs_doctor_createsSlot_whenDoctorIdIsHisOwn() {
        var slot = scheduleService.createAvailabilitySlotAs(user, doctor.getId(), LocalDateTime.now().plusDays(1), 30);
        assertThat(slot).isNotNull();
        assertThat(slot.getDoctor().getId()).isEqualTo(doctor.getId());
    }

    @Test
    void createSlotAs_doctor_throwsUnauthorizedAccessException_whenDoctorIdIsAColleague(){
        var otherDoctor = createDoctor("pippo@test.or");
        assertThrows(UnauthorizedAccessException.class, () -> scheduleService.createAvailabilitySlotAs(user, otherDoctor.getId(), LocalDateTime.now().plusDays(1), 30));
    }

    @Test
    void createSlotAs_admin_createsSlotForTheGivenDoctor () {
        var admin = userRepository.save(UserEntity.builder()
                .email("admin@test.it")
                .passwordHash("password")
                .role(Role.ADMIN)
                .build());
        var slot = scheduleService.createAvailabilitySlotAs(admin, doctor.getId(), LocalDateTime.now().plusDays(1), 30);
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
        assertThrows(IllegalArgumentException.class, () -> scheduleService.createAvailabilitySlotAs(admin, null, LocalDateTime.now().plusDays(1), 30));
    }

    @Test
    void test_slotCreationAndSearch(){
        LocalDateTime now = LocalDate.now().plusDays(1).atTime(9,0);
        scheduleService.createAvailabilitySlotAs(user, doctor.getId(), now, 30);

        var availabilities = scheduleService.searchAvailability(specialization.getId(), doctor.getId(), now.toLocalDate(), now.toLocalDate());

        assertThat(availabilities).hasSize(1);
        assertThat(availabilities.get(0).isBooked()).isFalse();
    }

    @Test
    void createSlotAs_throwsIllegalArgumentException_whenStartTimeIsInThePast() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> scheduleService.createAvailabilitySlotAs(user, null, LocalDateTime.now().minusHours(1), 30));
    }

    @Test
    void createSlotAs_throwsIllegalArgumentException_whenDurationIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> scheduleService.createAvailabilitySlotAs(user, null, LocalDateTime.now().plusHours(1), 0));
    }

    @Test
    void searchAvailabilities_excludeBookedSlots(){
        var day = LocalDate.now().plusDays(1);
        saveSlot(doctor, day.atTime(9, 0), true);
        var free = saveSlot(doctor, day.atTime(10, 0), false);
        var result = scheduleService.searchAvailability(specialization.getId(), null, day, day);

        assertThat(result).isNotNull();
        assertThat(result).extracting(AvailabilitySlotEntity::getId).containsExactly(free.getId());
    }

    @Test
    void searchAvailability_excludeOtherDays() {
        var day = LocalDate.now().plusDays(1);
        saveSlot(doctor, day.minusDays(1).atTime(23, 59), false);
        saveSlot(doctor, day.plusDays(1).atStartOfDay(), false);
        assertThat(scheduleService.searchAvailability(specialization.getId(), null, day, day)).isEmpty();
    }

    @Test
    void searchAvailability_excludesOtherSpecializations() {
        var otherSpecializations = specializationRepository.save(SpecializationEntity.builder().name("Dermatologia-test").build());
        var otherUser = userRepository.save(UserEntity.builder().email("dermatologo.test@email.com").passwordHash("password").role(Role.DOCTOR).build());
        var dermaDoc = doctorRepository.save(DoctorEntity.builder().firstName("Piero").lastName("Tamberi").user(otherUser).specialization(otherSpecializations).build());
        var day = LocalDate.now().plusDays(1);
        saveSlot(dermaDoc, day.atTime(9, 0), false);

        assertThat(scheduleService.searchAvailability(specialization.getId(), null, day, day)).isEmpty();
    }

    @Test
    void searchAvailability_returnsSlotsOrderedByStartTime() {
        var day = LocalDate.now().plusDays(1);
        saveSlot(doctor, day.atTime(9, 0), false);
        saveSlot(doctor, day.atTime(15, 0), false);

        var result = scheduleService.searchAvailability(specialization.getId(), null, day, day);

        assertThat(result).isNotNull();
        assertThat(result).extracting(AvailabilitySlotEntity::getStartTime).containsExactly(day.atTime(9, 0), day.atTime(15, 0));
    }

    // ---- search by range, doctor and specialization ---------------------------------------------

    @Test
    void searchAvailability_filtersByDoctor() {
        var day = LocalDate.now().plusDays(1);
        var colleague = createDoctor("colleague.search@test.it");
        saveSlot(doctor, day.atTime(9, 0), false);
        var colleagueSlot = saveSlot(colleague, day.atTime(10, 0), false);

        var result = scheduleService.searchAvailability(specialization.getId(), colleague.getId(), day, day);

        assertThat(result).extracting(AvailabilitySlotEntity::getId).containsExactly(colleagueSlot.getId());
    }

    @Test
    void searchAvailability_withoutDoctor_returnsAllTheDoctorsOfTheSpecialization() {
        var day = LocalDate.now().plusDays(1);
        var colleague = createDoctor("colleague.all@test.it");
        saveSlot(doctor, day.atTime(9, 0), false);
        saveSlot(colleague, day.atTime(10, 0), false);

        assertThat(scheduleService.searchAvailability(specialization.getId(), null, day, day)).hasSize(2);
    }

    @Test
    void searchAvailability_combinesSpecializationAndDoctor() {
        var day = LocalDate.now().plusDays(1);
        var derma = specializationRepository.save(SpecializationEntity.builder().name("Dermatologia-range-test").build());
        var dermaDoctor = createDoctorOf(derma, "derma.range@test.it");
        saveSlot(dermaDoctor, day.atTime(9, 0), false);

        assertThat(scheduleService.searchAvailability(specialization.getId(), dermaDoctor.getId(), day, day)).isEmpty();
        assertThat(scheduleService.searchAvailability(derma.getId(), dermaDoctor.getId(), day, day)).hasSize(1);
    }

    @Test
    void searchAvailability_excludesDeactivatedDoctors() {
        var day = LocalDate.now().plusDays(1);
        saveSlot(doctor, day.atTime(9, 0), false);
        user.setEnabled(false);
        userRepository.save(user);

        assertThat(scheduleService.searchAvailability(specialization.getId(), null, day, day)).isEmpty();
    }

    @Test
    void searchAvailability_excludesTheHoursAlreadyGoneToday() {
        var today = LocalDate.now();
        saveSlot(doctor, LocalDateTime.now().minusMinutes(30), false);

        assertThat(scheduleService.searchAvailability(specialization.getId(), null, today, today)).isEmpty();
    }

    @Test
    void searchAvailability_coversTheWholeRange_bothEndsIncluded() {
        var from = LocalDate.now().plusDays(2);
        var to = from.plusDays(2);
        saveSlot(doctor, from.atTime(8, 0), false);
        saveSlot(doctor, from.plusDays(1).atTime(12, 0), false);
        saveSlot(doctor, to.atTime(23, 30), false);
        saveSlot(doctor, to.plusDays(1).atTime(8, 0), false);

        var result = scheduleService.searchAvailability(specialization.getId(), null, from, to);

        assertThat(result).extracting(AvailabilitySlotEntity::getStartTime)
                .containsExactly(from.atTime(8, 0), from.plusDays(1).atTime(12, 0), to.atTime(23, 30));
    }

    @Test
    void searchAvailability_throwsIllegalArgumentException_whenTheEndPrecedesTheStart() {
        var day = LocalDate.now().plusDays(1);

        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.searchAvailability(specialization.getId(), null, day, day.minusDays(1)));
    }

    @Test
    void searchAvailability_acceptsExactly62Days_andRefuses63() {
        var from = LocalDate.now().plusDays(1);

        assertThat(scheduleService.searchAvailability(specialization.getId(), null, from, from.plusDays(61))).isEmpty();
        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.searchAvailability(specialization.getId(), null, from, from.plusDays(62)));
    }

    // ---- the slots of the doctor ----------------------------------------------------------------

    @Test
    void findMySlots_returnsFreeAndBookedSlots_ofTheLoggedDoctorOnly() {
        var day = LocalDate.now().plusDays(1);
        var colleague = createDoctor("colleague.mine@test.it");
        var free = saveSlot(doctor, day.atTime(9, 0), false);
        var booked = saveSlot(doctor, day.atTime(10, 0), true);
        saveSlot(colleague, day.atTime(11, 0), false);

        var result = scheduleService.findMySlots(user, day, day);

        assertThat(result).extracting(AvailabilitySlotEntity::getId).containsExactly(free.getId(), booked.getId());
    }

    @Test
    void findMySlots_excludesTheSlotsOutsideTheRange() {
        var day = LocalDate.now().plusDays(1);
        saveSlot(doctor, day.minusDays(1).atTime(23, 30), false);
        saveSlot(doctor, day.plusDays(1).atTime(0, 0), false);

        assertThat(scheduleService.findMySlots(user, day, day)).isEmpty();
    }

    // ---- overlap of the slots of a doctor -------------------------------------------------------

    @Test
    void createSlot_throwsSlotOverlapException_whenItStartsInsideAnExistingSlot() {
        var start = LocalDate.now().plusDays(1).atTime(9, 0);
        saveSlot(doctor, start, false);

        assertThrows(SlotOverlapException.class,
                () -> scheduleService.createAvailabilitySlotAs(user, null, start.plusMinutes(15), 30));
    }

    @Test
    void createSlot_throwsSlotOverlapException_whenItContainsAnExistingSlot() {
        var start = LocalDate.now().plusDays(1).atTime(9, 0);
        saveSlot(doctor, start.plusMinutes(15), false);

        assertThrows(SlotOverlapException.class,
                () -> scheduleService.createAvailabilitySlotAs(user, null, start, 60));
    }

    @Test
    void createSlot_throwsSlotOverlapException_whenItEndsInsideAnExistingSlot() {
        var start = LocalDate.now().plusDays(1).atTime(9, 0);
        saveSlot(doctor, start, false);

        assertThrows(SlotOverlapException.class,
                () -> scheduleService.createAvailabilitySlotAs(user, null, start.minusMinutes(15), 30));
    }

    @Test
    void createSlot_succeeds_whenItOnlyTouchesTheEndPointsOfAnExistingSlot() {
        var start = LocalDate.now().plusDays(1).atTime(9, 0);
        saveSlot(doctor, start, false);

        assertThat(scheduleService.createAvailabilitySlotAs(user, null, start.plusMinutes(30), 30)).isNotNull();
        assertThat(scheduleService.createAvailabilitySlotAs(user, null, start.minusMinutes(30), 30)).isNotNull();
    }

    @Test
    void createSlot_succeeds_forAnotherDoctorAtTheSameTime() {
        var start = LocalDate.now().plusDays(1).atTime(9, 0);
        var colleague = createDoctor("colleague.overlap@test.it");
        saveSlot(colleague, start, false);

        assertThat(scheduleService.createAvailabilitySlotAs(user, null, start, 30)).isNotNull();
    }

    @Test
    void createSlot_throwsIllegalArgumentException_whenTheDurationIsMoreThan240Minutes() {
        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.createAvailabilitySlotAs(user, null, LocalDate.now().plusDays(1).atTime(9, 0), 241));
    }

    // ---- creation of many slots -----------------------------------------------------------------

    @Test
    void createSlots_createsConsecutiveSlots() {
        var day = LocalDate.now().plusDays(1);

        var slots = scheduleService.createAvailabilitySlotsAs(user, null, day, LocalTime.of(9, 0), LocalTime.of(11, 0), 30);

        assertThat(slots).extracting(AvailabilitySlotEntity::getStartTime)
                .containsExactly(day.atTime(9, 0), day.atTime(9, 30), day.atTime(10, 0), day.atTime(10, 30));
        assertThat(slots).allSatisfy(slot -> {
            assertThat(slot.getDurationMinutes()).isEqualTo(30);
            assertThat(slot.isBooked()).isFalse();
            assertThat(slot.getDoctor().getId()).isEqualTo(doctor.getId());
        });
    }

    @Test
    void createSlots_dropsTheLastSlotThatDoesNotFit() {
        var day = LocalDate.now().plusDays(1);

        var slots = scheduleService.createAvailabilitySlotsAs(user, null, day, LocalTime.of(9, 0), LocalTime.of(10, 45), 30);

        assertThat(slots).hasSize(3);
        assertThat(slots.get(2).endTime()).isEqualTo(day.atTime(10, 30));
    }

    @Test
    void createSlots_createsNothing_whenOneOfThemOverlapsAnExistingSlot() {
        var day = LocalDate.now().plusDays(1);
        saveSlot(doctor, day.atTime(10, 0), false);

        assertThrows(SlotOverlapException.class,
                () -> scheduleService.createAvailabilitySlotsAs(user, null, day, LocalTime.of(9, 0), LocalTime.of(11, 0), 30));

        assertThat(scheduleService.findMySlots(user, day, day)).hasSize(1);
    }

    @Test
    void createSlots_succeeds_whenTheFirstOnlyTouchesAnExistingSlot() {
        var day = LocalDate.now().plusDays(1);
        saveSlot(doctor, day.atTime(9, 0), false);

        var slots = scheduleService.createAvailabilitySlotsAs(user, null, day, LocalTime.of(9, 30), LocalTime.of(10, 30), 30);

        assertThat(slots).hasSize(2);
    }

    @Test
    void createSlots_acceptsExactly48SlotsAndRefusesMore() {
        var day = LocalDate.now().plusDays(1);

        var slots = scheduleService.createAvailabilitySlotsAs(user, null, day, LocalTime.of(8, 0), LocalTime.of(12, 0), 5);

        assertThat(slots).hasSize(48);
        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.createAvailabilitySlotsAs(user, null, day.plusDays(1), LocalTime.of(8, 0), LocalTime.of(12, 5), 5));
    }

    @Test
    void createSlots_throwsIllegalArgumentException_whenTheEndIsNotAfterTheStart() {
        var day = LocalDate.now().plusDays(1);

        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.createAvailabilitySlotsAs(user, null, day, LocalTime.of(10, 0), LocalTime.of(10, 0), 30));
        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.createAvailabilitySlotsAs(user, null, day, LocalTime.of(10, 0), LocalTime.of(9, 0), 30));
    }

    @Test
    void createSlots_throwsIllegalArgumentException_whenTheRangeIsShorterThanOneVisit() {
        var day = LocalDate.now().plusDays(1);

        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.createAvailabilitySlotsAs(user, null, day, LocalTime.of(9, 0), LocalTime.of(9, 20), 30));
    }

    @Test
    void createSlots_throwsIllegalArgumentException_whenItStartsInThePast() {
        var yesterday = LocalDate.now().minusDays(1);

        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.createAvailabilitySlotsAs(user, null, yesterday, LocalTime.of(9, 0), LocalTime.of(10, 0), 30));
    }

    @Test
    void createSlots_throwsIllegalArgumentException_whenTheDurationIsOutOfRange() {
        var day = LocalDate.now().plusDays(1);

        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.createAvailabilitySlotsAs(user, null, day, LocalTime.of(9, 0), LocalTime.of(10, 0), 4));
        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.createAvailabilitySlotsAs(user, null, day, LocalTime.of(8, 0), LocalTime.of(20, 0), 241));
    }

    @Test
    void createSlots_admin_createsThemForTheGivenDoctor_andNeedsTheDoctorId() {
        var admin = userRepository.save(UserEntity.builder().email("admin.bulk@test.it").passwordHash("password").role(Role.ADMIN).build());
        var day = LocalDate.now().plusDays(1);

        var slots = scheduleService.createAvailabilitySlotsAs(admin, doctor.getId(), day, LocalTime.of(9, 0), LocalTime.of(10, 0), 30);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).getDoctor().getId()).isEqualTo(doctor.getId());
        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.createAvailabilitySlotsAs(admin, null, day.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0), 30));
    }

    @Test
    void createSlots_throwsUnauthorizedAccessException_whenADoctorNamesAColleague() {
        var colleague = createDoctor("colleague.bulk@test.it");
        var day = LocalDate.now().plusDays(1);

        assertThrows(UnauthorizedAccessException.class,
                () -> scheduleService.createAvailabilitySlotsAs(user, colleague.getId(), day, LocalTime.of(9, 0), LocalTime.of(10, 0), 30));
    }

    private DoctorEntity createDoctor(String email) {
        var user = userRepository.save(UserEntity.builder().passwordHash("PASSWORD").role(Role.DOCTOR).email(email).build());
        return doctorRepository.save(DoctorEntity.builder().user(user).firstName("Gianpaolo").lastName("Cortelesi").specialization(specialization).build());
    }

    private AvailabilitySlotEntity saveSlot(DoctorEntity doctor, LocalDateTime startTime, boolean booked) {
        return slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).startTime(startTime).durationMinutes(30).booked(booked).build());
    }

    private DoctorEntity createDoctorOf(SpecializationEntity specializationOfDoctor, String email) {
        var doctorUser = userRepository.save(UserEntity.builder().passwordHash("PASSWORD").role(Role.DOCTOR).email(email).build());
        return doctorRepository.save(DoctorEntity.builder().user(doctorUser).firstName("Piero").lastName("Tamberi").specialization(specializationOfDoctor).build());
    }
}
