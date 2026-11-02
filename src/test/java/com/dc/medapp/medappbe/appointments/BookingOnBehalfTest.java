package com.dc.medapp.medappbe.appointments;

import com.dc.medapp.medappbe.appointments.dto.CreateAppointmentRequest;
import com.dc.medapp.medappbe.audit.AuditLogRepository;
import com.dc.medapp.medappbe.audit.AuditOutcome;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.delegations.DelegationStatus;
import com.dc.medapp.medappbe.delegations.DoctorDelegationEntity;
import com.dc.medapp.medappbe.delegations.DoctorDelegationRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A doctor can schedule an appointment for a patient for himself,
 * but cannot schedule an appointment for a colleague (he will receive 403)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@AutoConfigureMockMvc
public class BookingOnBehalfTest {

    private static final String PATH = "/api/v1/appointments";

    @Autowired
    private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpecializationRepository specializationRepository;
    @Autowired private AvailabilitySlotRepository slotRepository;
    @Autowired private DoctorDelegationRepository delegationRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private ApiTestClient api;
    private DoctorEntity doctor;
    private DoctorEntity colleague;
    private String doctorToken;
    private String adminToken;
    private String patientToken;
    private long patientId;
    private long slotOfTheDoctor;
    private long slotOfTheColleague;
    private int hour;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-behalf-test").build());
        doctor = api.createDoctorWithConsentFlag("behalf.doctor@test.it", true, specialization);
        colleague = api.createDoctorWithConsentFlag("behalf.colleague@test.it", true, specialization);
        doctorToken = api.login("behalf.doctor@test.it");
        api.createUser("behalf.admin@test.it", Role.ADMIN);
        adminToken = api.login("behalf.admin@test.it");
        patientToken = api.registerPatient("behalf.patient@test.it");
        patientId = patientIdOf("behalf.patient@test.it");
        slotOfTheDoctor = newSlot(doctor);
        slotOfTheColleague = newSlot(colleague);
    }

    private long newSlot(DoctorEntity forDoctor) {
        return slotRepository.save(AvailabilitySlotEntity.builder().doctor(forDoctor).booked(false)
                .durationMinutes(30).startTime(LocalDateTime.now().plusDays(2).plusHours(hour++)).build()).getId();
    }

    private long patientIdOf(String email) {
        return patientRepository.findByUserId(userRepository.findByEmailIgnoreCase(email).orElseThrow().getId()).orElseThrow().getId();
    }

    private ResultActions book(String token, long slotId, Long onBehalfOf) throws Exception {
        return api.postAs(token, PATH, new CreateAppointmentRequest(slotId, onBehalfOf));
    }

    // ---- the doctor -----------------------------------------------------------------------------

    @Test
    void doctor_booksOnTheirOwnSlot_forARegisteredPatient() throws Exception {
        book(doctorToken, slotOfTheDoctor, patientId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patientId))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.bookedByRole").value("DOCTOR"));
    }

    @Test
    void thePatient_seesThatTheStudioBookedForThem() throws Exception {
        book(doctorToken, slotOfTheDoctor, patientId).andExpect(status().isOk());

        api.getAs(patientToken, PATH + "/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookedByRole").value("DOCTOR"));
    }

    @Test
    void doctor_cannotBookOnTheSlotOfAColleague_receives403() throws Exception {
        book(doctorToken, slotOfTheColleague, patientId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
        assertThat(slotRepository.findById(slotOfTheColleague).orElseThrow().isBooked()).isFalse();
    }

    @Test
    void doctor_booksOnTheSlotOfAColleagueWhoDelegatedThem() throws Exception {
        delegationRepository.save(DoctorDelegationEntity.builder()
                .delegatingDoctor(colleague).delegateDoctor(doctor)
                .startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(5))
                .status(DelegationStatus.ACCEPTED).build());

        book(doctorToken, slotOfTheColleague, patientId).andExpect(status().isOk());
    }

    @Test
    void doctor_withoutThePatient_receives400() throws Exception {
        book(doctorToken, slotOfTheDoctor, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    // ---- the admin ------------------------------------------------------------------------------

    @Test
    void admin_booksOnTheSlotOfAnyDoctor() throws Exception {
        book(adminToken, slotOfTheColleague, patientId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookedByRole").value("ADMIN"));
    }

    @Test
    void admin_withoutThePatient_receives400() throws Exception {
        book(adminToken, slotOfTheColleague, null).andExpect(status().isBadRequest());
    }

    // ---- the patient ----------------------------------------------------------------------------

    @Test
    void patient_booksForThemselves_andTheRoleIsPatient() throws Exception {
        book(patientToken, slotOfTheDoctor, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patientId))
                .andExpect(jsonPath("$.bookedByRole").value("PATIENT"));
    }

    @Test
    void patient_mayNameThemselves() throws Exception {
        book(patientToken, slotOfTheDoctor, patientId).andExpect(status().isOk());
    }

    @Test
    void patient_cannotBookForAnotherPatient_receives403() throws Exception {
        api.registerPatient("behalf.other@test.it");
        var otherId = patientIdOf("behalf.other@test.it");

        book(patientToken, slotOfTheDoctor, otherId).andExpect(status().isForbidden());
        assertThat(slotRepository.findById(slotOfTheDoctor).orElseThrow().isBooked()).isFalse();
    }

    // ---- the patient who is booked for ----------------------------------------------------------

    @Test
    void anUnknownPatient_receives404() throws Exception {
        book(adminToken, slotOfTheDoctor, 999_999L).andExpect(status().isNotFound());
    }

    @Test
    void anAnonymizedPatient_receives404() throws Exception {
        var patient = patientRepository.findById(patientId).orElseThrow();
        patient.setAnonymized(true);
        patientRepository.save(patient);

        book(adminToken, slotOfTheDoctor, patientId).andExpect(status().isNotFound());
    }

    @Test
    void thePatientCannotBeInTwoPlacesAtOnce_evenIfTheStudioBooks() throws Exception {
        var overlapping = slotRepository.save(AvailabilitySlotEntity.builder().doctor(colleague).booked(false).durationMinutes(30)
                .startTime(slotRepository.findById(slotOfTheDoctor).orElseThrow().getStartTime()).build()).getId();
        book(patientToken, slotOfTheDoctor, null).andExpect(status().isOk());

        book(adminToken, overlapping, patientId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_OVERLAP"));
    }

    @Test
    void anAlreadyBookedSlot_receives409_whoeverBooks() throws Exception {
        book(patientToken, slotOfTheDoctor, null).andExpect(status().isOk());
        api.registerPatient("behalf.second@test.it");

        book(doctorToken, slotOfTheDoctor, patientIdOf("behalf.second@test.it"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
    }

    @Test
    void withoutLogin_receives401() throws Exception {
        mockMvc.perform(post(PATH).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(slotOfTheDoctor, patientId))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyBooking_isInTheAccessLog() throws Exception {
        book(doctorToken, slotOfTheDoctor, patientId).andExpect(status().isOk());
        book(doctorToken, slotOfTheColleague, patientId).andExpect(status().isForbidden());

        assertThat(auditLogRepository.findAll()).filteredOn(line -> "BOOK_APPOINTMENT".equals(line.getAction()))
                .extracting("outcome").contains(AuditOutcome.SUCCESS, AuditOutcome.FAILURE);
    }
}
