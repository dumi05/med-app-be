package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.audit.AuditLogRepository;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.doctor.dto.CreateDoctorRequest;
import com.dc.medapp.medappbe.schedule.dto.UpdateWorkingHoursRequest;
import com.dc.medapp.medappbe.schedule.dto.WorkingHoursBand;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import com.dc.medapp.medappbe.support.ApiTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class WorkingHoursTest {

    private static final String PATH = "/api/v1/schedule/working-hours";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private SpecializationRepository specializationRepository;
    @Autowired private AvailabilitySlotRepository slotRepository;
    @Autowired private WorkingHoursRepository workingHoursRepository;
    @Autowired private SlotGenerationJob job;
    @Autowired private AuditLogRepository auditLogRepository;

    private ApiTestClient api;
    private SpecializationEntity specialization;
    private DoctorEntity doctor;
    private DoctorEntity colleague;
    private String doctorToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-hours-test").build());
        doctor = api.createDoctorWithConsentFlag("hours.doctor@test.it", true, specialization);
        colleague = api.createDoctorWithConsentFlag("hours.colleague@test.it", true, specialization);
        doctorToken = api.login("hours.doctor@test.it");
        api.createUser("hours.admin@test.it", Role.ADMIN);
        adminToken = api.login("hours.admin@test.it");
    }

    private static WorkingHoursBand band(DayOfWeek day, String from, String to) {
        return new WorkingHoursBand(day, LocalTime.parse(from), LocalTime.parse(to));
    }

    private ResultActions replace(String token, Long doctorId, WorkingHoursBand... bands) throws Exception {
        return api.putAs(token, PATH, new UpdateWorkingHoursRequest(doctorId, List.of(bands)));
    }

    private List<AvailabilitySlotEntity> slotsOf(DoctorEntity forDoctor) {
        return slotRepository.findByDoctorIdAndStartTimeBetweenOrderByStartTime(forDoctor.getId(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusYears(1));
    }

    // ---- replace the hours ----------------------------------------------------------------------

    @Test
    void doctor_setsTheHours_andTheSlotsAreCreatedForTheNextWeeks() throws Exception {
        var response = replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "11:00"), band(DayOfWeek.THURSDAY, "15:00", "16:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bands.length()").value(2))
                .andExpect(jsonPath("$.bands[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.bands[0].startTime").value("09:00:00"))
                .andReturn().getResponse().getContentAsString();

        var created = objectMapper.readTree(response).get("createdSlots").asInt();
        // 8 weeks: eight Mondays with 4 slots and eight Thursdays with 2, less what has already passed today
        assertThat(created).isBetween(8 * 6 - 6, 8 * 6);
        assertThat(slotsOf(doctor)).hasSize(created).allSatisfy(slot -> assertThat(slot.isGenerated()).isTrue());
    }

    @Test
    void theGeneratedSlots_areFoundByThePatient_whoSearchesTheAvailability() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.TUESDAY, "09:00", "10:00")).andExpect(status().isOk());
        var patientToken = api.registerPatient("hours.patient@test.it");
        var from = LocalDateTime.now().toLocalDate();

        api.getAs(patientToken, "/api/v1/schedule/availability?specializationId=" + specialization.getId() + "&doctorId=" + doctor.getId()
                        + "&from=" + from + "&to=" + from.plusDays(14))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void theHoursAreReadBack_sortedByDayAndTime() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.WEDNESDAY, "15:00", "16:00"), band(DayOfWeek.MONDAY, "15:00", "16:00"), band(DayOfWeek.MONDAY, "09:00", "10:00"))
                .andExpect(status().isOk());

        api.getAs(doctorToken, PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bands[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.bands[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.bands[1].startTime").value("15:00:00"))
                .andExpect(jsonPath("$.bands[2].dayOfWeek").value("WEDNESDAY"));
    }

    @Test
    void changingTheHours_replacesTheFreeSlots() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isOk());

        var response = replace(doctorToken, null, band(DayOfWeek.MONDAY, "15:00", "16:00"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).get("removedSlots").asInt()).isPositive();
        assertThat(slotsOf(doctor)).isNotEmpty()
                .allSatisfy(slot -> assertThat(slot.getStartTime().getHour()).isEqualTo(15));
    }

    @Test
    void anEmptyList_meansNoWorkingHours_andTakesAwayTheFreeSlots() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isOk());

        replace(doctorToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.bands.length()").value(0));

        assertThat(slotsOf(doctor)).isEmpty();
        assertThat(workingHoursRepository.findByDoctorId(doctor.getId())).isEmpty();
    }

    @Test
    void whenTheHoursChange_aBookedSlot_aSlotMadeByHand_andTheSlotOfACancelledVisitStay() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "11:00")).andExpect(status().isOk());
        var generated = slotsOf(doctor);
        var patientToken = api.registerPatient("hours.keeper@test.it");
        var booked = generated.get(0);
        var cancelled = generated.get(1);
        api.book(patientToken, booked.getId());
        var cancelledAppointment = api.book(patientToken, cancelled.getId());
        api.postAs(patientToken, "/api/v1/appointments/" + cancelledAppointment + "/cancel", null).andExpect(status().isOk());
        var byHand = slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor)
                .startTime(LocalDateTime.now().plusDays(3).withHour(20).withMinute(0).withSecond(0).withNano(0))
                .durationMinutes(30).booked(false).build());

        replace(doctorToken, null).andExpect(status().isOk());

        assertThat(slotsOf(doctor)).extracting(AvailabilitySlotEntity::getId)
                .containsExactlyInAnyOrder(booked.getId(), cancelled.getId(), byHand.getId());
    }

    @Test
    void aSlotMarkedAsBooked_neverLeaves_evenIfNoAppointmentPointsToIt() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isOk());
        var marked = slotsOf(doctor).get(0);
        marked.setBooked(true);
        slotRepository.saveAndFlush(marked);

        replace(doctorToken, null).andExpect(status().isOk());

        assertThat(slotsOf(doctor)).extracting(AvailabilitySlotEntity::getId).containsExactly(marked.getId());
    }

    @Test
    void aNewHoursListDoesNotOverlapTheSlotThatIsKept() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isOk());
        var patientToken = api.registerPatient("hours.kept@test.it");
        var kept = slotsOf(doctor).get(0);
        api.book(patientToken, kept.getId());

        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isOk());

        var sameTime = slotsOf(doctor).stream().filter(slot -> slot.getStartTime().equals(kept.getStartTime())).toList();
        assertThat(sameTime).hasSize(1);
        assertThat(sameTime.get(0).isBooked()).isTrue();
    }

    // ---- what is not allowed --------------------------------------------------------------------

    @Test
    void sunday_isRefused() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.SUNDAY, "09:00", "10:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void aBandThatEndsBeforeItStarts_orAtTheSameTime_isRefused() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "10:00", "09:00")).andExpect(status().isBadRequest());
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "10:00", "10:00")).andExpect(status().isBadRequest());
    }

    @Test
    void aBandShorterThanASlot_isRefused() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "09:29")).andExpect(status().isBadRequest());
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "09:30")).andExpect(status().isOk());
    }

    @Test
    void aTimeThatIsNotOnFiveMinutes_isRefused() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:03", "11:00")).andExpect(status().isBadRequest());
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:05", "11:00")).andExpect(status().isOk());
    }

    @Test
    void twoBandsOfTheSameDay_cannotOverlap_butCanTouch() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "11:00"), band(DayOfWeek.MONDAY, "10:30", "12:00"))
                .andExpect(status().isBadRequest());
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "11:00"), band(DayOfWeek.MONDAY, "11:00", "12:00"))
                .andExpect(status().isOk());
    }

    @Test
    void aDayWithMoreThanThreeBands_isRefused() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "08:00", "09:00"), band(DayOfWeek.MONDAY, "10:00", "11:00"),
                band(DayOfWeek.MONDAY, "12:00", "13:00"), band(DayOfWeek.MONDAY, "14:00", "15:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRefusedList_changesNothing() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isOk());
        var before = slotsOf(doctor).size();

        replace(doctorToken, null, band(DayOfWeek.SUNDAY, "09:00", "10:00")).andExpect(status().isBadRequest());

        assertThat(slotsOf(doctor)).hasSize(before);
        assertThat(workingHoursRepository.findByDoctorId(doctor.getId())).hasSize(1);
    }

    @Test
    void aMissingDayOrTime_returns400() throws Exception {
        mockMvc.perform(put(PATH).header("Authorization", "Bearer " + doctorToken).contentType("application/json")
                        .content("{\"bands\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---- who ------------------------------------------------------------------------------------

    @Test
    void doctor_cannotSetTheHoursOfAColleague_receives403() throws Exception {
        replace(doctorToken, colleague.getId(), band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isForbidden());
        assertThat(slotsOf(colleague)).isEmpty();
    }

    @Test
    void admin_setsTheHoursOfADoctor() throws Exception {
        replace(adminToken, colleague.getId(), band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isOk());
        api.getAs(adminToken, PATH + "?doctorId=" + colleague.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bands.length()").value(1));
        assertThat(slotsOf(colleague)).isNotEmpty();
    }

    @Test
    void admin_withoutTheDoctor_receives400() throws Exception {
        replace(adminToken, null, band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isBadRequest());
        api.getAs(adminToken, PATH).andExpect(status().isBadRequest());
    }

    @Test
    void admin_withAnUnknownDoctor_receives404() throws Exception {
        replace(adminToken, 999_999L, band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isNotFound());
    }

    @Test
    void aPatient_cannotSeeNorSetTheHours_receives403() throws Exception {
        var patientToken = api.registerPatient("hours.nope@test.it");

        api.getAs(patientToken, PATH).andExpect(status().isForbidden());
        replace(patientToken, null, band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isForbidden());
    }

    @Test
    void withoutLogin_receives401() throws Exception {
        mockMvc.perform(put(PATH).contentType("application/json").content("{\"bands\":[]}")).andExpect(status().isUnauthorized());
    }

    // ---- a new doctor and the night job ---------------------------------------------------------

    @Test
    void aDoctorCreatedByTheAdmin_startsWithTheUsualHours_andWithSlots() throws Exception {
        var created = api.postAs(adminToken, "/api/v1/doctors",
                        new CreateDoctorRequest("hours.newdoc@test.it", ApiTestClient.PASSWORD, "Giulia", "Verdi", specialization.getId(), true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var newId = objectMapper.readTree(created).get("id").asLong();

        api.getAs(adminToken, PATH + "?doctorId=" + newId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bands.length()").value(11))
                .andExpect(jsonPath("$.bands[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.bands[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.bands[10].dayOfWeek").value("SATURDAY"))
                .andExpect(jsonPath("$.bands[10].endTime").value("13:00:00"));
        assertThat(slotRepository.findByDoctorIdAndStartTimeBetweenOrderByStartTime(newId, LocalDateTime.now(), LocalDateTime.now().plusWeeks(9)))
                .hasSizeGreaterThan(300)
                .noneMatch(slot -> slot.getStartTime().getDayOfWeek() == DayOfWeek.SUNDAY);
    }

    @Test
    void theNightJob_createsTheSlotsOfTheDoctorsWithHours() {
        workingHoursRepository.save(WorkingHoursEntity.builder().doctor(doctor).dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0)).build());
        assertThat(slotsOf(doctor)).isEmpty();

        job.run();

        assertThat(slotsOf(doctor)).isNotEmpty();
        assertThat(slotsOf(colleague)).isEmpty();
    }

    @Test
    void theNightJob_skipsADeactivatedDoctor() {
        workingHoursRepository.save(WorkingHoursEntity.builder().doctor(doctor).dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0)).build());
        doctor.getUser().setEnabled(false);
        userRepository.save(doctor.getUser());

        job.run();

        assertThat(slotsOf(doctor)).isEmpty();
    }

    @Test
    void everyChangeOfTheHours_isInTheAccessLog() throws Exception {
        replace(doctorToken, null, band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isOk());
        replace(doctorToken, colleague.getId(), band(DayOfWeek.MONDAY, "09:00", "10:00")).andExpect(status().isForbidden());

        assertThat(auditLogRepository.findAll()).filteredOn(line -> "UPDATE_WORKING_HOURS".equals(line.getAction())).hasSize(2);
    }
}