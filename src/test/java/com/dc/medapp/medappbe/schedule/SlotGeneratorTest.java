package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import com.dc.medapp.medappbe.support.ApiTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SlotGeneratorTest {

    private static final LocalDateTime MONDAY_MORNING = LocalDateTime.of(2030,1,7,8,0);

    @Autowired private SlotGenerator generator;
    @Autowired private WorkingHoursRepository workingHoursRepository;
    @Autowired private AvailabilitySlotRepository slotRepository;
    @Autowired private SpecializationRepository specializationRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private DoctorEntity doc;

    @BeforeEach
    void setUp() {
        var api = new ApiTestClient(null, objectMapper, userRepository, passwordEncoder, doctorRepository);
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-test").build());
        doc = api.createDoctorWithConsentFlag("gen.doc@test.it", false, specialization);
    }

    private void band(DayOfWeek day, int fromHour, int fromMinute, int toHour, int toMinute) {
        workingHoursRepository.save(
                WorkingHoursEntity.builder()
                        .doctor(doc)
                        .dayOfWeek(day)
                        .startTime(LocalTime.of(fromHour, fromMinute))
                        .endTime(LocalTime.of(toHour, toMinute))
                        .build()
        );
    }

    private List<AvailabilitySlotEntity> slots() {
        return slotRepository.findByDoctorIdAndStartTimeBetweenOrderByStartTime(
                doc.getId(),
                LocalDateTime.of(2029, 1,1,0,0),
                LocalDateTime.of(2031,1,1,0,0));
    }

    @Test
    void itCreatesTheSlotsForEveryBand_forEightWeeks(){
        band(DayOfWeek.MONDAY, 9,0,11,0);

        var created = generator.generate(doc, MONDAY_MORNING);

        assertThat(created).isEqualTo(4*8);
        assertThat(slots()).hasSize(32)
                .allSatisfy(slot -> {
                    assertThat(slot.isGenerated()).isTrue();
                    assertThat(slot.isBooked()).isFalse();
                    assertThat(slot.getDurationMinutes()).isEqualTo(30);
                    assertThat(slot.getStartTime().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
                });
        assertThat(slots().get(0).getStartTime()).isEqualTo(LocalDateTime.of(2030,1,7,9,0));
        assertThat(slots().get(31).getStartTime()).isEqualTo(LocalDateTime.of(2030,2,25,10,30));
    }

    @Test
    void aDayCanHaveAMorningAndAnAfternoon(){
        band(DayOfWeek.MONDAY, 9,0,10,0);
        band(DayOfWeek.MONDAY, 15,0,16,0);

        generator.generate(doc, MONDAY_MORNING);

        assertThat(slots().stream().filter(x -> x.getStartTime().toLocalDate().equals(LocalDate.of(2030,1,7))))
                .extracting(slot -> slot.getStartTime().toLocalTime())
                .containsExactly(LocalTime.of(9,0), LocalTime.of(9,30), LocalTime.of(15,0), LocalTime.of(15,30));
    }

    @Test
    void everyBandIsOnItsOwnDayOfTheWeekOnly(){
        band(DayOfWeek.WEDNESDAY, 9,0,10,0);
        band(DayOfWeek.SATURDAY, 9,0,10,0);

        generator.generate(doc, MONDAY_MORNING);
        assertThat(slots()).extracting(slot -> slot.getStartTime().getDayOfWeek()).containsOnly(DayOfWeek.SATURDAY, DayOfWeek.WEDNESDAY);
    }

    @Test
    void theSlotsAlreadyPassedAreNotCreated_theFirstOneIsAfterNow(){
        band(DayOfWeek.MONDAY,9,0,13,0);

        generator.generate(doc, LocalDateTime.of(2030,1,7,10,15));

        assertThat(slots().get(0).getStartTime()).isEqualTo(LocalDateTime.of(2030,1,7,10,30));
    }

    @Test
    void aSlotThatStartExactlyNow_isNotCreated(){
        band(DayOfWeek.MONDAY, 9,0,11,0);

        generator.generate(doc, LocalDateTime.of(2030,1,7,10,0));

        assertThat(slots().get(0).getStartTime()).isEqualTo(LocalDateTime.of(2030,1,7,10,30));
    }

    @Test
    void itLeavesOutWhatOverlapsASlotAlreadyThere() {
        band(DayOfWeek.MONDAY, 9,0,11,0);

        slotRepository.save(AvailabilitySlotEntity.builder()
                .doctor(doc)
                .startTime(LocalDateTime.of(2030,1,7,9,15))
                .durationMinutes(30)
                .booked(false)
                .build());

        generator.generate(doc, MONDAY_MORNING);

        assertThat(slots().stream().filter(x -> x.getStartTime().toLocalDate().equals(LocalDate.of(2030,1,7))))
                .extracting(x -> x.getStartTime().toLocalTime())
                .containsExactly(LocalTime.of(9,15), LocalTime.of(10,0), LocalTime.of(10,30));
    }

    @Test
    void runningItAgain_createsNothingNew() {
        band(DayOfWeek.MONDAY, 9,0,11,0);

        generator.generate(doc, MONDAY_MORNING);

        var second = generator.generate(doc, MONDAY_MORNING);

        assertThat(second).isZero();
        assertThat(slots()).hasSize(32);
    }

    @Test
    void aBandThatIsNotAMultipleOfTheSlot_dropsTheLastPiece() {
        band(DayOfWeek.MONDAY, 9,0,10,20);

        generator.generate(doc, MONDAY_MORNING);

        assertThat(slots().stream().filter(x -> x.getStartTime().toLocalDate().equals(LocalDate.of(2030,1,7))))
                .extracting(x -> x.getStartTime().toLocalTime())
                .containsExactly(LocalTime.of(9,0), LocalTime.of(9,30));
    }

    @Test
    void aDoctorWithoutWorkingHours_getsNothing(){
        assertThat(generator.generate(doc, MONDAY_MORNING)).isZero();
        assertThat(slots()).isEmpty();
    }

    @Test
    void sundayIsNeverGenerated_evenIfABandIsThere() {
        band(DayOfWeek.SUNDAY, 9,0,11,0);

        assertThat(generator.generate(doc, MONDAY_MORNING)).isZero();
    }

    @Test
    void itContinuesFromTheDayItIsRun_soTheWindowMovesForward(){
        band(DayOfWeek.MONDAY, 9,0,10,0);

        generator.generate(doc, MONDAY_MORNING);

        var created = generator.generate(doc, MONDAY_MORNING.plusWeeks(1));

        assertThat(created).isEqualTo(2);
        assertThat(slots().get(slots().size()-1).getStartTime()).isEqualTo(LocalDateTime.of(2030,3,4,9,30));
    }
}
