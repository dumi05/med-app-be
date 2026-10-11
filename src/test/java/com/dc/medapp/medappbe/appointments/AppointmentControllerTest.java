package com.dc.medapp.medappbe.appointments;

import com.dc.medapp.medappbe.appointments.dto.CreateAppointmentRequest;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AppointmentControllerTest {
    private static final String PASSWORD = "password123";
    private static final String DOCTOR_EMAIL = "doctor@email.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;

    private SpecializationEntity specialization;
    private Long slotId;


    @BeforeEach
    void setUp() {
        specialization = specializationRepository.save(SpecializationEntity.builder()
                        .name("Cardiologia-test")
                .build());
        slotId = createSlot(createDoctor(DOCTOR_EMAIL));
    }

    @Test
    void patient_canBookListAndCancelOwnAppointment() throws Exception {
        var token = registerPatient("patient1@email.com");
        var appointmentId= book(token, slotId);

        mockMvc.perform(get("/api/v1/appointments/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(appointmentId))
                .andExpect(jsonPath("$[0].status").value("BOOKED"))
                .andExpect(jsonPath("$[0].noShowRisk").doesNotExist());
        mockMvc.perform(post("/api/v1/appointments/"+appointmentId+"/cancel").header("Authorization", "Bearer "+token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void patient_cannotCancelAnotherPatientsAppointment_receives409() throws Exception {
        var ownerToken = registerPatient("pat.owner@email.com");
        var otherToken = registerPatient("pat.other@email.com");
        var appointmentId = book(ownerToken, slotId);

        mockMvc.perform(post("/api/v1/appointments/"+appointmentId+"/cancel").header("Authorization", "Bearer "+otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    void patient_bookingAnAlreadyBookedSlot_receives409() throws Exception {
        book(registerPatient("patient.1@pat.lo"), slotId);
        var secondToken = registerPatient("patient.2@pat.lo");

        mockMvc.perform(post("/api/v1/appointments").header("Authorization", "Bearer "+secondToken).contentType("application/json").content(objectMapper.writeValueAsString(new CreateAppointmentRequest(slotId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
    }

    @Test
    void doctor_cannotBookAnAppointment_receives403() throws Exception {
        var doctorToken = login(DOCTOR_EMAIL);

        mockMvc.perform(post("/api/v1/appointments").header("Authorization", "Bearer "+doctorToken).contentType("application/json").content(objectMapper.writeValueAsString(new CreateAppointmentRequest(slotId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignedDoctor_canCompleteVisitAndSeesNoShowRisk() throws Exception {
        var appointmentId = book(registerPatient("patient.1@pat.lo"), slotId);
        var doctorToken = login(DOCTOR_EMAIL);

        mockMvc.perform(get("/api/v1/appointments/me").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(appointmentId))
                .andExpect(jsonPath("$[0].noShowRisk").value(false));

        mockMvc.perform(post("/api/v1/appointments/"+appointmentId+"/complete").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void otherDoctor_cannotCompleteVisit_receives403() throws Exception {
        var appointmentId = book(registerPatient("patient.2@doc.lo"), slotId);
        createDoctor("other.doc@email.ot");
        var otherDocToken = login("other.doc@email.ot");

        mockMvc.perform(post("/api/v1/appointments/"+appointmentId+"/complete").header("Authorization", "Bearer " + otherDocToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    void patient_cannotCompleteVisit_receives403() throws Exception {
        var token = registerPatient("patient.visiter@test.oo");
        var appointmentId = book(token, slotId);

        mockMvc.perform(post("/api/v1/appointments/"+appointmentId+"/complete").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String registerPatient(String mail) throws Exception {
        var request = new RegisterRequest(mail, PASSWORD, "Luca", "Amauri", LocalDate.of(1994,5,2), true, "v1.0");
        var json = mockMvc.perform(post("/api/v1/auth/register")
                                        .contentType("application/json").content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asString();
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

    private Long createSlot(DoctorEntity doc){
        return availabilitySlotRepository.save(
                AvailabilitySlotEntity.builder()
                        .doctor(doc)
                        .booked(false)
                        .durationMinutes(30)
                        .startTime(LocalDateTime.now().plusDays(2))
                        .build()
        ).getId();
    }

    private long book(String token, Long slotId) throws Exception {
        var json = mockMvc.perform(post("/api/v1/appointments").header("Authorization", "Bearer " + token).contentType("application/json").content(objectMapper.writeValueAsString(new CreateAppointmentRequest(slotId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }
}
