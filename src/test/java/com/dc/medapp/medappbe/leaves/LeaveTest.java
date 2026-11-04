package com.dc.medapp.medappbe.leaves;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.appointments.CancellationReason;
import com.dc.medapp.medappbe.audit.AuditLogRepository;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.leaves.dto.CreateLeaveRequest;
import com.dc.medapp.medappbe.news.NewsRepository;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.schedule.WorkingHoursEntity;
import com.dc.medapp.medappbe.schedule.WorkingHoursRepository;
import com.dc.medapp.medappbe.schedule.dto.BulkSlotRequest;
import com.dc.medapp.medappbe.schedule.dto.SlotRequest;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class LeaveTest {

    private static final String PATH = "/api/v1/doctor-leaves";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpecializationRepository specializationRepository;
    @Autowired private AvailabilitySlotRepository slotRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private DoctorLeaveRepository leaveRepository;
    @Autowired private NewsRepository newsRepository;
    @Autowired private WorkingHoursRepository workingHoursRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private ApiTestClient api;
    private SpecializationEntity cardiology;
    private DoctorEntity away;          // the doctor who goes away
    private DoctorEntity colleague;     // same specialization
    private DoctorEntity other;         // another specialization
    private String awayToken;
    private String colleagueToken;
    private String adminToken;
    private String patient1;
    private String patient2;
    private String patient3;

    private LocalDate from;             // the leave: day(9) .. day(11)
    private LocalDate to;
    private AvailabilitySlotEntity awayAt10;       // day(10) 10:00, the colleague is free at 10:00
    private AvailabilitySlotEntity awayAt11;       // day(10) 11:00, the colleague has nothing at 11:00
    private AvailabilitySlotEntity colleagueAt10;
    private AvailabilitySlotEntity awayLater;      // day(30), outside the leave
    private long visit1;                           // patient 1, awayAt10: it can move
    private long visit2;                           // patient 2, awayAt11: it cannot
    private long visit3;                           // patient 3, awayLater: not touched

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        cardiology = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-leave-test").build());
        var dermatology = specializationRepository.save(SpecializationEntity.builder().name("Dermatologia-leave-test").build());
        away = doctor("leave.away@test.it", cardiology, "Mario", "Rossi");
        colleague = doctor("leave.colleague@test.it", cardiology, "Luca", "Bianchi");
        other = doctor("leave.other@test.it", dermatology, "Anna", "Verdi");
        awayToken = api.login("leave.away@test.it");
        colleagueToken = api.login("leave.colleague@test.it");
        api.createUser("leave.admin@test.it", Role.ADMIN);
        adminToken = api.login("leave.admin@test.it");
        patient1 = api.registerPatient("leave.patient1@test.it");
        patient2 = api.registerPatient("leave.patient2@test.it");
        patient3 = api.registerPatient("leave.patient3@test.it");

        from = day(9);
        to = day(11);
        awayAt10 = slot(away, day(10).atTime(10, 0), 30);
        awayAt11 = slot(away, day(10).atTime(11, 0), 30);
        colleagueAt10 = slot(colleague, day(10).atTime(10, 0), 30);
        awayLater = slot(away, day(30).atTime(10, 0), 30);
        visit1 = api.book(patient1, awayAt10.getId());
        visit2 = api.book(patient2, awayAt11.getId());
        visit3 = api.book(patient3, awayLater.getId());
    }

    private DoctorEntity doctor(String email, SpecializationEntity specialization, String first, String last) {
        var created = api.createDoctorWithConsentFlag(email, true, specialization);
        created.setFirstName(first);
        created.setLastName(last);
        return doctorRepository.save(created);
    }

    private static LocalDate day(int daysAhead) {
        return LocalDate.now().plusDays(daysAhead);
    }

    /** The first date from {@code earliest} on that falls on the given day of the week. */
    private static LocalDate next(DayOfWeek dayOfWeek, int earliestDaysAhead) {
        var date = day(earliestDaysAhead);
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.plusDays(1);
        }
        return date;
    }

    private AvailabilitySlotEntity slot(DoctorEntity doctor, LocalDateTime start, int minutes) {
        return slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).startTime(start).durationMinutes(minutes).booked(false).build());
    }

    private void worksOn(DoctorEntity doctor, DayOfWeek... days) {
        for (var dayOfWeek : days) {
            workingHoursRepository.save(WorkingHoursEntity.builder().doctor(doctor).dayOfWeek(dayOfWeek)
                    .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0)).build());
        }
    }

    private ResultActions register(String token, Long doctorId, LocalDate start, LocalDate end, Long replacementId) throws Exception {
        return api.postAs(token, PATH, new CreateLeaveRequest(doctorId, start, end, replacementId));
    }

    private ResultActions preview(String token, Long doctorId, LocalDate start, LocalDate end, Long replacementId) throws Exception {
        return api.getAs(token, PATH + "/preview?startDate=" + start + "&endDate=" + end
                + (doctorId == null ? "" : "&doctorId=" + doctorId) + (replacementId == null ? "" : "&replacementDoctorId=" + replacementId));
    }

    private AppointmentEntity reload(long id) {
        appointmentRepository.flush();
        return appointmentRepository.findById(id).orElseThrow();
    }

    private String newsBodyOf(String token) throws Exception {
        var json = api.getAs(token, "/api/v1/news").andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get(0).get("body").asString();
    }

    // ---- the preview ----------------------------------------------------------------------------

    @Test
    void thePreview_changesNothing_andSaysWhatWouldHappenToEveryVisit() throws Exception {
        preview(awayToken, null, from, to, colleague.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments.length()").value(2))
                .andExpect(jsonPath("$.reassign").value(1))
                .andExpect(jsonPath("$.cancel").value(1))
                .andExpect(jsonPath("$.appointments[0].id").value(visit1))
                .andExpect(jsonPath("$.appointments[0].outcome").value("REASSIGN"))
                .andExpect(jsonPath("$.appointments[0].patientLastName").value("Amauri"))
                .andExpect(jsonPath("$.appointments[1].id").value(visit2))
                .andExpect(jsonPath("$.appointments[1].outcome").value("CANCEL"))
                .andExpect(jsonPath("$.newsTitle").value("Dott. Mario Rossi: assente dal " + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(from)
                        + " al " + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(to)));

        assertThat(reload(visit1).getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(reload(visit1).getSlot().getId()).isEqualTo(awayAt10.getId());
        assertThat(leaveRepository.count()).isZero();
        assertThat(newsRepository.count()).isZero();
    }

    @Test
    void thePreview_withoutAColleague_cancelsEverything() throws Exception {
        preview(awayToken, null, from, to, null)
                .andExpect(jsonPath("$.reassign").value(0))
                .andExpect(jsonPath("$.cancel").value(2));
    }

    @Test
    void thePreview_warnsWhenTheNoticeIsShort() throws Exception {
        preview(awayToken, null, day(2), day(3), null)
                .andExpect(jsonPath("$.daysOfNotice").value(2))
                .andExpect(jsonPath("$.shortNotice").value(true));
        preview(awayToken, null, day(7), day(8), null).andExpect(jsonPath("$.shortNotice").value(false));
        preview(awayToken, null, day(6), day(8), null).andExpect(jsonPath("$.shortNotice").value(true));
        preview(awayToken, null, LocalDate.now(), day(1), null)
                .andExpect(jsonPath("$.daysOfNotice").value(0))
                .andExpect(jsonPath("$.shortNotice").value(true));
    }

    // ---- registering the leave ------------------------------------------------------------------

    @Test
    void theVisitMovesToTheColleague_andTheOneThatCannotMoveIsCancelled() throws Exception {
        register(awayToken, null, from, to, colleague.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reassigned").value(1))
                .andExpect(jsonPath("$.cancelled").value(1));

        var moved = reload(visit1);
        assertThat(moved.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(moved.getSlot().getId()).isEqualTo(colleagueAt10.getId());
        assertThat(moved.getSlot().isBooked()).isTrue();
        assertThat(moved.getReassignedFrom().getId()).isEqualTo(away.getId());
        assertThat(moved.getReassignedAt()).isNotNull();
        assertThat(slotRepository.findById(awayAt10.getId()).orElseThrow().isBooked()).isFalse();

        var cancelled = reload(visit2);
        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo(CancellationReason.DOCTOR_ABSENCE);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(slotRepository.findById(awayAt11.getId()).orElseThrow().isBooked()).isFalse();
    }

    @Test
    void aVisitOutsideTheLeave_isNotTouched() throws Exception {
        register(awayToken, null, from, to, colleague.getId()).andExpect(status().isOk());

        var untouched = reload(visit3);
        assertThat(untouched.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(untouched.getSlot().getId()).isEqualTo(awayLater.getId());
        assertThat(untouched.getReassignedFrom()).isNull();
    }

    @Test
    void withoutAColleague_everyVisitOfTheLeaveIsCancelled() throws Exception {
        register(awayToken, null, from, to, null)
                .andExpect(jsonPath("$.reassigned").value(0))
                .andExpect(jsonPath("$.cancelled").value(2));

        assertThat(reload(visit1).getCancellationReason()).isEqualTo(CancellationReason.DOCTOR_ABSENCE);
    }

    @Test
    void thePatientSees_whatHappenedToTheirVisit() throws Exception {
        register(awayToken, null, from, to, colleague.getId()).andExpect(status().isOk());

        api.getAs(patient1, "/api/v1/appointments/me")
                .andExpect(jsonPath("$[0].status").value("BOOKED"))
                .andExpect(jsonPath("$[0].reassignedFromName").value("Mario Rossi"))
                .andExpect(jsonPath("$[0].slot.doctorLastName").value("Bianchi"));
        api.getAs(patient2, "/api/v1/appointments/me")
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].cancellationReason").value("DOCTOR_ABSENCE"));
    }

    @Test
    void aVisitCancelledByThePatient_saysSo() throws Exception {
        api.postAs(patient3, "/api/v1/appointments/" + visit3 + "/cancel", null).andExpect(status().isOk());

        api.getAs(patient3, "/api/v1/appointments/me").andExpect(jsonPath("$[0].cancellationReason").value("PATIENT"));
        assertThat(reload(visit3).getCancellationReason()).isEqualTo(CancellationReason.PATIENT);
    }

    @Test
    void onlyTheVisitsStillToBeDone_areTouched() throws Exception {
        var patient = patientRepository.findAll().get(0);
        for (var status : new AppointmentStatus[]{AppointmentStatus.COMPLETED, AppointmentStatus.INVOICED, AppointmentStatus.REPORTED, AppointmentStatus.CANCELLED}) {
            var slot = slot(away, day(10).atTime(14 + status.ordinal(), 0), 30);
            slot.setBooked(status != AppointmentStatus.CANCELLED);
            appointmentRepository.save(AppointmentEntity.builder().slot(slot).patient(patient).status(status).noShowRisk(false).build());
        }

        register(awayToken, null, from, to, colleague.getId())
                .andExpect(jsonPath("$.reassigned").value(1))
                .andExpect(jsonPath("$.cancelled").value(1));
    }

    @Test
    void aVisitThatAlreadyStartedToday_isNotTouched_whenTheLeaveStartsToday() throws Exception {
        var patient = patientRepository.findAll().get(0);
        var earlier = slot(away, LocalDateTime.now().minusHours(1), 30);
        earlier.setBooked(true);
        var started = appointmentRepository.save(AppointmentEntity.builder().slot(earlier).patient(patient).status(AppointmentStatus.BOOKED).noShowRisk(false).build());

        register(awayToken, null, LocalDate.now(), day(1), null).andExpect(status().isOk());

        assertThat(reload(started.getId()).getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void theColleagueWhoIsAwayThatDay_cannotTakeTheVisit() throws Exception {
        register(colleagueToken, null, from, to, null).andExpect(status().isOk());

        register(awayToken, null, from, to, colleague.getId())
                .andExpect(jsonPath("$.reassigned").value(0))
                .andExpect(jsonPath("$.cancelled").value(2));
    }

    @Test
    void aSlotOfTheColleagueThatIsTooShort_cannotTakeTheVisit() throws Exception {
        colleagueAt10.setDurationMinutes(15);
        slotRepository.save(colleagueAt10);
        register(awayToken, null, from, to, colleague.getId()).andExpect(jsonPath("$.reassigned").value(0));
    }

    @Test
    void aLongerSlotOfTheColleague_takesTheVisit() throws Exception {
        colleagueAt10.setDurationMinutes(60);
        slotRepository.save(colleagueAt10);
        register(awayToken, null, from, to, colleague.getId()).andExpect(jsonPath("$.reassigned").value(1));
    }

    @Test
    void aSlotOfTheColleagueThatIsAlreadyBooked_cannotTakeTheVisit() throws Exception {
        api.book(patient3, colleagueAt10.getId());

        register(awayToken, null, from, to, colleague.getId()).andExpect(jsonPath("$.reassigned").value(0));
    }

    // ---- what is not allowed --------------------------------------------------------------------

    @Test
    void theColleagueMustBeAnotherDoctor_ofTheSameSpecialization_andActive() throws Exception {
        register(awayToken, null, from, to, away.getId()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        register(awayToken, null, from, to, other.getId()).andExpect(status().isBadRequest());
        register(awayToken, null, from, to, 999_999L).andExpect(status().isNotFound());
        colleague.getUser().setEnabled(false);
        userRepository.save(colleague.getUser());
        register(awayToken, null, from, to, colleague.getId()).andExpect(status().isBadRequest());
        assertThat(leaveRepository.count()).isZero();
    }

    @Test
    void theDates_mustMakeSense() throws Exception {
        register(awayToken, null, day(5), day(4), null).andExpect(status().isBadRequest());
        register(awayToken, null, day(-5), day(-1), null).andExpect(status().isBadRequest());
        register(awayToken, null, day(5), day(5 + 90), null).andExpect(status().isBadRequest());
        register(awayToken, null, day(5), day(5 + 89), null).andExpect(status().isOk());
    }

    @Test
    void aLeaveCanStartToday_orInThePast_ifItEndsTodayOrLater() throws Exception {
        register(awayToken, null, LocalDate.now(), LocalDate.now(), null).andExpect(status().isOk());
        register(colleagueToken, null, day(-2), LocalDate.now(), null).andExpect(status().isOk());
    }

    @Test
    void twoLeavesOfTheSameDoctor_cannotOverlap_butCanTouch() throws Exception {
        register(awayToken, null, day(20), day(22), null).andExpect(status().isOk());

        register(awayToken, null, day(22), day(24), null).andExpect(status().isBadRequest());
        register(awayToken, null, day(18), day(20), null).andExpect(status().isBadRequest());
        register(awayToken, null, day(23), day(24), null).andExpect(status().isOk());
        register(awayToken, null, day(25), day(26), null).andExpect(status().isOk());
    }

    @Test
    void aMissingDate_returns400() throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + awayToken).contentType("application/json").content("{\"startDate\":\"" + day(5) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---- who ------------------------------------------------------------------------------------

    @Test
    void aDoctor_cannotRegisterTheLeaveOfAColleague_receives403() throws Exception {
        register(awayToken, colleague.getId(), from, to, null).andExpect(status().isForbidden());
        preview(awayToken, colleague.getId(), from, to, null).andExpect(status().isForbidden());
        assertThat(leaveRepository.count()).isZero();
    }

    @Test
    void theAdmin_registersTheLeaveForADoctor_andTheNewsIsInTheNameOfThatDoctor() throws Exception {
        register(adminToken, away.getId(), from, to, colleague.getId()).andExpect(status().isOk());

        var leave = leaveRepository.findAll().get(0);
        assertThat(leave.getCreatedByRole()).isEqualTo(Role.ADMIN);
        assertThat(leave.getDoctor().getId()).isEqualTo(away.getId());
        assertThat(newsRepository.findAll().get(0).getAuthor().getId()).isEqualTo(away.getId());
    }

    @Test
    void theAdmin_mustSayWhichDoctor() throws Exception {
        register(adminToken, null, from, to, null).andExpect(status().isBadRequest());
        preview(adminToken, null, from, to, null).andExpect(status().isBadRequest());
    }

    @Test
    void aPatient_cannotUseTheLeaves_receives403() throws Exception {
        register(patient1, null, from, to, null).andExpect(status().isForbidden());
        preview(patient1, null, from, to, null).andExpect(status().isForbidden());
        api.getAs(patient1, PATH).andExpect(status().isForbidden());
    }

    @Test
    void withoutLogin_receives401() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    // ---- what the leave does to the rest --------------------------------------------------------

    @Test
    void theSlotsOfTheLeaveDays_areNoLongerOffered_theOthersAre() throws Exception {
        var freeInLeave = slot(away, day(10).atTime(16, 0), 30);
        register(awayToken, null, from, to, null).andExpect(status().isOk());

        api.getAs(patient3, "/api/v1/schedule/availability?specializationId=" + cardiology.getId() + "&doctorId=" + away.getId() + "&from=" + from + "&to=" + to)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + freeInLeave.getId() + ")]").isEmpty());
        api.getAs(patient3, "/api/v1/schedule/availability?specializationId=" + cardiology.getId() + "&from=" + from + "&to=" + to)
                .andExpect(jsonPath("$[?(@.doctorId == " + away.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.doctorId == " + colleague.getId() + ")]").isNotEmpty());
        slot(away, day(12).atTime(10, 0), 30);
        api.getAs(patient3, "/api/v1/schedule/availability?specializationId=" + cardiology.getId() + "&doctorId=" + away.getId() + "&from=" + day(12) + "&to=" + day(12))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aSlotOfTheLeave_cannotBeBooked() throws Exception {
        var freeInLeave = slot(away, day(10).atTime(16, 0), 30);
        register(awayToken, null, from, to, null).andExpect(status().isOk());

        api.postAs(patient3, "/api/v1/appointments", new com.dc.medapp.medappbe.appointments.dto.CreateAppointmentRequest(freeInLeave.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
    }

    @Test
    void noSlotCanBeCreatedByHandOnADayOfTheLeave() throws Exception {
        register(awayToken, null, from, to, null).andExpect(status().isOk());

        api.postAs(awayToken, "/api/v1/schedule/slot", new SlotRequest(null, day(10).atTime(18, 0), 30)).andExpect(status().isBadRequest());
        api.postAs(awayToken, "/api/v1/schedule/slots/bulk", new BulkSlotRequest(null, day(10), LocalTime.of(18, 0), LocalTime.of(19, 0), 30)).andExpect(status().isBadRequest());
        api.postAs(awayToken, "/api/v1/schedule/slot", new SlotRequest(null, day(12).atTime(18, 0), 30)).andExpect(status().isOk());
    }

    // ---- list and early return ------------------------------------------------------------------

    @Test
    void theList_showsTheLeavesToCome_withTheColleague() throws Exception {
        register(awayToken, null, from, to, colleague.getId()).andExpect(status().isOk());
        leaveRepository.save(DoctorLeaveEntity.builder().doctor(away).startDate(day(-10)).endDate(day(-5)).createdByRole(Role.DOCTOR).build());

        api.getAs(awayToken, PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].replacementDoctorName").value("Luca Bianchi"));
        api.getAs(adminToken, PATH + "?doctorId=" + away.getId()).andExpect(jsonPath("$.length()").value(1));
        api.getAs(colleagueToken, PATH).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void comingBackEarly_removesTheLeaveAndItsNews_butNotWhatHappenedToTheVisits() throws Exception {
        var id = objectMapper.readTree(register(awayToken, null, from, to, colleague.getId()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertThat(newsRepository.count()).isEqualTo(1);

        api.deleteAs(awayToken, PATH + "/" + id).andExpect(status().isNoContent());

        assertThat(leaveRepository.count()).isZero();
        assertThat(newsRepository.count()).isZero();
        assertThat(reload(visit1).getSlot().getId()).isEqualTo(colleagueAt10.getId());
        assertThat(reload(visit2).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        var freeAgain = slot(away, day(10).atTime(16, 0), 30);
        api.getAs(patient3, "/api/v1/schedule/availability?specializationId=" + cardiology.getId() + "&doctorId=" + away.getId() + "&from=" + from + "&to=" + to)
                .andExpect(jsonPath("$[?(@.id == " + freeAgain.getId() + ")]").isNotEmpty());
    }

    @Test
    void whoCannotCancelTheLeave_andWhatCannotBeCancelled() throws Exception {
        var id = objectMapper.readTree(register(awayToken, null, from, to, null).andReturn().getResponse().getContentAsString()).get("id").asLong();
        var finished = leaveRepository.save(DoctorLeaveEntity.builder().doctor(away).startDate(day(-10)).endDate(day(-5)).createdByRole(Role.DOCTOR).build());

        api.deleteAs(colleagueToken, PATH + "/" + id).andExpect(status().isForbidden());
        api.deleteAs(patient1, PATH + "/" + id).andExpect(status().isForbidden());
        api.deleteAs(awayToken, PATH + "/999999").andExpect(status().isNotFound());
        api.deleteAs(awayToken, PATH + "/" + finished.getId()).andExpect(status().isBadRequest());
        api.deleteAs(adminToken, PATH + "/" + id).andExpect(status().isNoContent());
    }

    // ---- the news -------------------------------------------------------------------------------

    @Test
    void theLeave_publishesANewsForThePatients_thatCannotBeDeletedByHand() throws Exception {
        register(awayToken, null, from, to, colleague.getId()).andExpect(status().isOk());

        api.getAs(patient3, "/api/v1/news")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].automatic").value(true))
                .andExpect(jsonPath("$[0].authorLastName").value("Rossi"))
                .andExpect(jsonPath("$[0].expiresAt").value(to.toString()));
        var newsId = newsRepository.findAll().get(0).getId();
        api.deleteAs(awayToken, "/api/v1/news/" + newsId).andExpect(status().isBadRequest());
    }

    @Test
    void theNews_saysWhoIsAway_untilWhen_andWhoToAsk_withoutTheWordHolidays() throws Exception {
        register(awayToken, null, from, to, colleague.getId()).andExpect(status().isOk());

        var body = newsBodyOf(patient3);
        assertThat(body).contains("Il dott. Mario Rossi sarà assente dal ")
                .contains("(inclusi)")
                .contains("In caso di necessità puoi rivolgerti al dott. Luca Bianchi.")
                .contains("controlla le tue prenotazioni")
                .doesNotContainIgnoringCase("ferie");
    }

    @Test
    void withoutAColleague_theNewsDoesNotSayWhoToAsk() throws Exception {
        register(awayToken, null, from, to, null).andExpect(status().isOk());

        assertThat(newsBodyOf(patient3)).doesNotContain("rivolgerti");
    }

    @Test
    void theDayOfTheReturn_isTheFirstWorkingDay_andSundayDoesNotCount() throws Exception {
        worksOn(away, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
        var friday = next(DayOfWeek.FRIDAY, 40);
        register(awayToken, null, friday.minusDays(2), friday, null).andExpect(status().isOk());

        assertThat(newsBodyOf(patient3)).contains("Tornerà disponibile da sabato " + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(friday.plusDays(1)));
    }

    @Test
    void aSundayBand_leftInTheData_neverMakesTheDayOfTheReturn() throws Exception {
        worksOn(away, DayOfWeek.SUNDAY, DayOfWeek.MONDAY);
        var saturday = next(DayOfWeek.SATURDAY, 40);
        register(awayToken, null, saturday, saturday, null).andExpect(status().isOk());

        assertThat(newsBodyOf(patient3)).contains("Tornerà disponibile da lunedì ");
    }

    @Test
    void aSaturdayThatEndsTheLeave_returnsOnMonday() throws Exception {
        worksOn(away, DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
        var saturday = next(DayOfWeek.SATURDAY, 40);
        register(awayToken, null, saturday, saturday, null).andExpect(status().isOk());

        assertThat(newsBodyOf(patient3)).contains("Tornerà disponibile da lunedì " + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(saturday.plusDays(2)));
    }

    @Test
    void aDoctorWhoDoesNotWorkOnSaturday_returnsOnMonday() throws Exception {
        worksOn(away, DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        var friday = next(DayOfWeek.FRIDAY, 40);
        register(awayToken, null, friday, friday, null).andExpect(status().isOk());

        assertThat(newsBodyOf(patient3)).contains("Tornerà disponibile da lunedì " + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(friday.plusDays(3)));
    }

    @Test
    void aLeaveRightAfter_isSkippedInTheDayOfTheReturn() throws Exception {
        worksOn(away, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
        var friday = next(DayOfWeek.FRIDAY, 40);
        // Saturday and Monday away as well: the doctor is back on Tuesday
        register(awayToken, null, friday.plusDays(1), friday.plusDays(3), null).andExpect(status().isOk());
        register(awayToken, null, friday, friday, null).andExpect(status().isOk());

        var json = api.getAs(patient3, "/api/v1/news").andReturn().getResponse().getContentAsString();
        var bodies = objectMapper.readTree(json);
        var returning = bodies.get(0).get("body").asString();
        assertThat(returning).contains("Tornerà disponibile da martedì " + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(friday.plusDays(4)));
    }

    @Test
    void withoutWorkingHours_theNewsDoesNotPromiseADay() throws Exception {
        register(awayToken, null, from, to, null).andExpect(status().isOk());

        assertThat(newsBodyOf(patient3)).doesNotContain("Tornerà");
    }

    @Test
    void theWordsOfTheNews_areItalian_whateverTheLanguageOfTheServerIs() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            var body = LeaveNewsText.body(away, LocalDate.of(2030, 1, 7), LocalDate.of(2030, 1, 11), java.util.Optional.of(LocalDate.of(2030, 1, 14)), null);

            assertThat(body).contains("dal 07/01/2030 al 11/01/2030").contains("lunedì 14/01/2030");
        } finally {
            Locale.setDefault(previous);
        }
    }

    // ---- the access log -------------------------------------------------------------------------

    @Test
    void theLeave_andEveryVisitItMovesOrCancels_areInTheAccessLog() throws Exception {
        register(awayToken, null, from, to, colleague.getId()).andExpect(status().isOk());

        var lines = auditLogRepository.findAll();
        assertThat(lines).filteredOn(line -> "CREATE_DOCTOR_LEAVE".equals(line.getAction())).hasSize(1);
        assertThat(lines).filteredOn(line -> "REASSIGN_APPOINTMENT".equals(line.getAction())).extracting("entityId").containsExactly(visit1);
        assertThat(lines).filteredOn(line -> "CANCEL_APPOINTMENT_ABSENCE".equals(line.getAction())).extracting("entityId").containsExactly(visit2);
    }
}