package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.appointments.dto.CreateAppointmentRequest;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.reports.dto.CreateMedicalReportRequest;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.dc.medapp.medappbe.billing.BillingService.STANDARD_VISIT_TYPE;
import static com.dc.medapp.medappbe.support.ApiTestClient.PASSWORD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class MedicalReportControllerTest {

    private static final String DOC_EMAIL = "doc@mail.et";
    private static final String PATIENT_MAIL = "pat@mail.co";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private AvailabilitySlotRepository slotRepository;
    @Autowired
    private PriceListRepository priceListRepository;

    private SpecializationEntity specialization;
    private DoctorEntity doctor;
    private String doctorToken;
    private String patientToken;
    private long completedAppointmentId;

    @BeforeEach
    void setUp () throws Exception {
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-report-test").build());
        priceListRepository.save(
                PriceListEntity.builder()
                        .specialization(specialization)
                        .visitType(STANDARD_VISIT_TYPE)
                        .price(new BigDecimal("100.00"))
                        .build());
        doctor = createDoctor(DOC_EMAIL);
        doctorToken = login(DOC_EMAIL);
        patientToken = registerPatient(PATIENT_MAIL);

        completedAppointmentId = book(patientToken, createSlot(doctor, 2));
        completeVisit(doctorToken, completedAppointmentId);
    }

    @Test
    void doc_canWriteTheReport_andTheAppointmentBecomesReported() throws Exception {
        postReport(doctorToken, createMedicalReportReq(completedAppointmentId, "Nessun risultato."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.appointmentId").value(completedAppointmentId))
                .andExpect(jsonPath("$.diagnosisText").value("Nessun risultato."));

        mockMvc.perform(get("/api/v1/appointments/me").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REPORTED"));
    }

    @Test
    void patient_cannotWriteAReport_receives403() throws Exception {
        postReport(patientToken, createMedicalReportReq(completedAppointmentId, "Nessun risultato."))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherDoc_cannotWriteTjeReport_receives403() throws Exception {
        createDoctor("other.doc@lol.ic");
        var otherDocToken = login("other.doc@lol.ic");
        postReport(otherDocToken, createMedicalReportReq(completedAppointmentId, "Nessun risultato."))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    void writingTheReportBeforeTheVisitIsCompleted_receives409() throws Exception {
        var stillBooked = book(registerPatient("second.pat@ui.com"), createSlot(doctor, 3));

        postReport(doctorToken, createMedicalReportReq(stillBooked, "Nessun risultato."))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void writingTheReportTwice_receives409() throws Exception {
        postReport(doctorToken, createMedicalReportReq(completedAppointmentId, "Nessun risultato.")).andExpect(status().isOk());
        postReport(doctorToken, createMedicalReportReq(completedAppointmentId, "Ancora Nessun risultato."))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void writingAReportWithABlankDiagnosis_receives400() throws Exception {
        postReport(doctorToken, createMedicalReportReq(completedAppointmentId, " ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void writingAReportWithoutAppointmentId_receives400() throws Exception {
        postReport(doctorToken, createMedicalReportReq(null, "Nessun risultato.")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }


    @Test
    void writingAReportForAnUnknownAppointment_receives404() throws Exception {
        postReport(doctorToken, createMedicalReportReq(999_999L, "Nessun risultato.")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void ownerPat_canReadTheReportById() throws Exception {
        var reportId = writeReport();

        mockMvc.perform(get("/api/v1/medical-reports/" + reportId).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosisText").value("Nessun risultato."));
    }

    @Test
    void ownerPat_canReadTheReportByAppointment() throws Exception {
        var reportId = writeReport();

        mockMvc.perform(get("/api/v1/medical-reports").param("appointmentId", String.valueOf(completedAppointmentId))
                .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId));
    }

    @Test
    void assignedDoc_canReadTheReport() throws Exception {
        var reportId = writeReport();

        mockMvc.perform(get("/api/v1/medical-reports/" + reportId).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    @Test
    void anotherPat_cannotReadTheReport_receives403 () throws  Exception {
        var reportId = writeReport();
        var otherPatToken = registerPatient("other.pat@lok.ik");

        mockMvc.perform(get("/api/v1/medical-reports/" + reportId).header("Authorization", "Bearer " + otherPatToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    void admin_cannotReadTheReport_receives403() throws Exception {
        var reportId = writeReport();
        createAdmin("report.admin@test.oc");
        var adminToken = login("report.admin@test.oc");

        mockMvc.perform(get("/api/v1/medical-reports/" + reportId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void readingAReportWithoutToken_receives401() throws  Exception {
        mockMvc.perform(get("/api/v1/medical-reports/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readingAnUnknownReport_receives404() throws Exception {
        mockMvc.perform(get("/api/v1/medical-reports/" + 999_999L).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }


    // Helpers

    private @NonNull CreateMedicalReportRequest createMedicalReportReq(Long completedAppointmentId, String diagnosis) {
        return new CreateMedicalReportRequest(completedAppointmentId, diagnosis);
    }

    private DoctorEntity createDoctor(String email) {
        var user = userRepository.save(UserEntity.builder().email(email).role(Role.DOCTOR).passwordHash(passwordEncoder.encode(PASSWORD)).createdAt(Instant.now()).build());
        return doctorRepository.save(DoctorEntity.builder().user(user).specialization(specialization).firstName("Anotnio").lastName("Tonio").build());
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

    private Long createSlot(DoctorEntity doctor, int daysFromNow) {
        return slotRepository.save(AvailabilitySlotEntity.builder()
                .doctor(doctor)
                .booked(false)
                .durationMinutes(30)
                .startTime(LocalDateTime.now().plusDays(daysFromNow))
                .build()
        ).getId();
    }

    private void createAdmin(String email){
        userRepository.save(UserEntity.builder()
                        .role(Role.ADMIN)
                        .passwordHash(passwordEncoder.encode(PASSWORD))
                        .email(email)
                .build());
    }

    private long book(String token, Long slotId) throws Exception {
        var json = mockMvc.perform(
                            post("/api/v1/appointments")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(slotId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private void completeVisit(String token, long completedAppointmentId) throws Exception {
        mockMvc.perform(post("/api/v1/appointments/" + completedAppointmentId + "/complete")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }


    private ResultActions postReport(String token, CreateMedicalReportRequest req) throws Exception {
        return postReport(token, req, null);
    }

    private long writeReport() throws Exception {
        var json = postReport(doctorToken, createMedicalReportReq(completedAppointmentId, "Nessun risultato."), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private ResultActions postReport(String token, CreateMedicalReportRequest req, MockMultipartFile file) throws Exception {
        mockMvc.perform(post("/api/v1/appointments/" + req.appointmentId() + "/invoice")
                .header("Authorization", "Bearer " + token));   // esito ignorato
        var request = multipart("/api/v1/medical-reports")
                .file(new MockMultipartFile("report", "", "application/json", objectMapper.writeValueAsBytes(req)))
                .header("Authorization", "Bearer " + token);
        if (file != null) {
            request.file(file);
        }
        return mockMvc.perform(request);
    }
}
