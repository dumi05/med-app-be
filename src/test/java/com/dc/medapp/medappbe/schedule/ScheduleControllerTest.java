package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.schedule.dto.BulkSlotRequest;
import com.dc.medapp.medappbe.schedule.dto.SlotRequest;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static com.dc.medapp.medappbe.support.ApiTestClient.PASSWORD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ScheduleControllerTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private AvailabilitySlotRepository slotRepository;

    private SpecializationEntity specialization;

    @BeforeEach
    void setUp() {
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-test").build());
    }

    @Test
    void createSlot_withStartTimeInThePast_returns400() throws Exception {
        createDoctor("myDoc@test.ok");
        var token = login("myDoc@test.ok");
        postSlot(token, new SlotRequest(null, LocalDateTime.now().minusDays(1), 30))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createSlot_withNegativeDuration_returns400() throws Exception {
        createDoctor("myDoc2@test.ok");
        var token = login("myDoc2@test.ok");
        postSlot(token, new SlotRequest(null, LocalDateTime.now().plusDays(1), -90))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }


    @Test
    void createSlot_withNonPositiveDuration_returns400() throws Exception {
        createDoctor("myDoc2@test.ok");
        var token = login("myDoc2@test.ok");
        postSlot(token, new SlotRequest(null, LocalDateTime.now().plusDays(1), 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createSlot_asAdmin_forUnknownDoctor_returns404() throws Exception {
        var token = createAdminAndLogin("admin@admin.com");
        postSlot(token, new SlotRequest(999_999L, LocalDateTime.now().plusDays(1), 30))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void slotCreationAs_loggedDoctor_withoutDoctorId() throws Exception {
        var doctor = createDoctor("doc@email.ot");
        var token = login("doc@email.ot");
        var request = new SlotRequest(null, LocalDateTime.now().plusDays(51), 30);
        postSlot(token, request).andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(doctor.getId()));
    }

    @Test
    void slotCreationAs_loggedDoctor_forAColleague_throwsUnauthorizedAccessException() throws Exception {
        createDoctor("doc@email.ot");
        var otherDoctor = createDoctor("doc2@email.ot");
        var token = login("doc@email.ot");
        var request = new SlotRequest(otherDoctor.getId(), LocalDateTime.now().plusDays(45), 30);
        postSlot(token, request).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    void createSlot_asPatient_returns403() throws Exception {
        var patientToken = registerPatient("patient.slot@test.it");
        postSlot(patientToken, new SlotRequest(null, LocalDateTime.now().plusDays(1), 30))
                .andExpect(status().isForbidden());
    }

    @Test
    void availability_isOpenToAnyAuthenticatedUser() throws  Exception {
        var patientToken = registerPatient("patient.search@hotmail.com");
        mockMvc.perform(get("/api/v1/schedule/availability").param("specializationId", String.valueOf(specialization.getId())).param("from", LocalDate.now().plusDays(1).toString()).param("to", LocalDate.now().plusDays(10).toString())
                .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void availability_withoutToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/schedule/availability").param("specializationId", String.valueOf(specialization.getId())).param("date", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isUnauthorized());
    }

    // ---- search by range ------------------------------------------------------------------------

    @Test
    void availability_returnsTheFreeSlotsOfTheRange_andFiltersByDoctor() throws Exception {
        var doctor = createDoctor("doc.range@test.ok");
        var colleague = createDoctor("doc.range2@test.ok");
        var day = LocalDate.now().plusDays(3);
        slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).startTime(day.atTime(9, 0)).durationMinutes(30).booked(false).build());
        slotRepository.save(AvailabilitySlotEntity.builder().doctor(colleague).startTime(day.atTime(10, 0)).durationMinutes(30).booked(false).build());
        var token = registerPatient("patient.range@test.it");

        search(token, "specializationId=" + specialization.getId() + "&from=" + day + "&to=" + day)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        search(token, "specializationId=" + specialization.getId() + "&doctorId=" + colleague.getId() + "&from=" + day + "&to=" + day)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].doctorId").value(colleague.getId()));
    }

    @Test
    void availability_withoutTheRange_returns400() throws Exception {
        var token = registerPatient("patient.norange@test.it");

        search(token, "specializationId=" + specialization.getId()).andExpect(status().isBadRequest());
    }

    @Test
    void availability_withARangeLongerThan62Days_returns400() throws Exception {
        var token = registerPatient("patient.longrange@test.it");
        var from = LocalDate.now().plusDays(1);

        search(token, "specializationId=" + specialization.getId() + "&from=" + from + "&to=" + from.plusDays(62))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    // ---- the slots of the doctor ----------------------------------------------------------------

    @Test
    void mySlots_returnsFreeAndBookedSlotsOfTheLoggedDoctor() throws Exception {
        var doctor = createDoctor("doc.mine@test.ok");
        var colleague = createDoctor("doc.mine2@test.ok");
        var day = LocalDate.now().plusDays(3);
        slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).startTime(day.atTime(9, 0)).durationMinutes(30).booked(false).build());
        slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).startTime(day.atTime(10, 0)).durationMinutes(30).booked(true).build());
        slotRepository.save(AvailabilitySlotEntity.builder().doctor(colleague).startTime(day.atTime(11, 0)).durationMinutes(30).booked(false).build());

        mockMvc.perform(get("/api/v1/schedule/slots/me?from=" + day + "&to=" + day).header("Authorization", "Bearer " + login("doc.mine@test.ok")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].booked").value(false))
                .andExpect(jsonPath("$[1].booked").value(true));
    }

    @Test
    void mySlots_asPatient_returns403_andWithoutTokenReturns401() throws Exception {
        var day = LocalDate.now().plusDays(1);
        var patientToken = registerPatient("patient.mine@test.it");

        mockMvc.perform(get("/api/v1/schedule/slots/me?from=" + day + "&to=" + day).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/schedule/slots/me?from=" + day + "&to=" + day))
                .andExpect(status().isUnauthorized());
    }

    // ---- creation of many slots -----------------------------------------------------------------

    @Test
    void bulk_asDoctor_createsTheSlots() throws Exception {
        var doctor = createDoctor("doc.bulk@test.ok");
        var token = login("doc.bulk@test.ok");

        postBulk(token, new BulkSlotRequest(null, LocalDate.now().plusDays(2), LocalTime.of(9, 0), LocalTime.of(11, 0), 30))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].doctorId").value(doctor.getId()))
                .andExpect(jsonPath("$[0].durationMinutes").value(30));
    }

    @Test
    void bulk_overlappingAnExistingSlot_returns409() throws Exception {
        var doctor = createDoctor("doc.bulk2@test.ok");
        var token = login("doc.bulk2@test.ok");
        var day = LocalDate.now().plusDays(2);
        slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).startTime(day.atTime(10, 0)).durationMinutes(30).booked(false).build());

        postBulk(token, new BulkSlotRequest(null, day, LocalTime.of(9, 0), LocalTime.of(11, 0), 30))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_OVERLAP"));
    }

    @Test
    void bulk_withASlotShorterThanTheMinimum_returns400() throws Exception {
        createDoctor("doc.bulk3@test.ok");
        var token = login("doc.bulk3@test.ok");

        postBulk(token, new BulkSlotRequest(null, LocalDate.now().plusDays(2), LocalTime.of(9, 0), LocalTime.of(11, 0), 3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void bulk_asAdminWithoutDoctorId_returns400() throws Exception {
        var token = createAdminAndLogin("admin.bulk@test.ok");

        postBulk(token, new BulkSlotRequest(null, LocalDate.now().plusDays(2), LocalTime.of(9, 0), LocalTime.of(11, 0), 30))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void bulk_asPatient_returns403() throws Exception {
        var token = registerPatient("patient.bulk@test.it");

        postBulk(token, new BulkSlotRequest(null, LocalDate.now().plusDays(2), LocalTime.of(9, 0), LocalTime.of(11, 0), 30))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSlot_overlappingAnExistingSlot_returns409() throws Exception {
        var doctor = createDoctor("doc.overlap@test.ok");
        var token = login("doc.overlap@test.ok");
        var start = LocalDate.now().plusDays(2).atTime(9, 0);
        slotRepository.save(AvailabilitySlotEntity.builder().doctor(doctor).startTime(start).durationMinutes(30).booked(false).build());

        postSlot(token, new SlotRequest(null, start.plusMinutes(10), 30))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_OVERLAP"));
    }

    @Test
    void createSlot_longerThan240Minutes_returns400() throws Exception {
        createDoctor("doc.long@test.ok");
        var token = login("doc.long@test.ok");

        postSlot(token, new SlotRequest(null, LocalDate.now().plusDays(2).atTime(9, 0), 241))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String registerPatient(String mail) throws Exception {
        var request = new RegisterRequest(mail, PASSWORD, "Luca", "Amauri", LocalDate.of(1994,5,2), true, "v1.0");
        var json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json").content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asString();
    }

    private String createAdminAndLogin(String mail) throws Exception {
        var user = userRepository.save(UserEntity.builder().passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.ADMIN).email(mail).build());
        return login(user.getEmail());
    }

    private String login(String mail) throws Exception {
        var json = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(new LoginRequest(mail, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asString();
    }
    private DoctorEntity createDoctor(String email) {
        var user = userRepository.save(UserEntity.builder().passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.DOCTOR).email(email).build());
        return doctorRepository.save(DoctorEntity.builder().user(user).firstName("Gianpaolo").lastName("Cortelesi").specialization(specialization).build());
    }

    private ResultActions postSlot(String token, SlotRequest slotRequest) throws Exception {
        return mockMvc.perform(post("/api/v1/schedule/slot").header("Authorization", "Bearer " + token).contentType("application/json").content(objectMapper.writeValueAsString(slotRequest)));
    }

    private ResultActions search(String token, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/schedule/availability?" + query).header("Authorization", "Bearer " + token));
    }

    private ResultActions postBulk(String token, BulkSlotRequest request) throws Exception {
        return mockMvc.perform(post("/api/v1/schedule/slots/bulk").header("Authorization", "Bearer " + token).contentType("application/json").content(objectMapper.writeValueAsString(request)));
    }
}


