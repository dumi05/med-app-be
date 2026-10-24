package com.dc.medapp.medappbe;

import com.dc.medapp.medappbe.appointments.dto.CreateAppointmentRequest;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.delegations.dto.CreateReportDelegationRequest;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.doctor.dto.CreateDoctorRequest;
import com.dc.medapp.medappbe.reports.dto.CreateMedicalReportRequest;
import com.dc.medapp.medappbe.schedule.dto.SlotRequest;
import com.dc.medapp.medappbe.support.ApiTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class AppointmentLifecycleIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@studio-medico.com";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String DOCTOR_EMAIL = "e2e.doc@studio-medico.com";
    private static final String PATIENT_EMAIL = "e2e.patient@studio-medico.com";
    private static final String DIAGNOSIS = "Nessuna criticità riscontrata.";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private ApiTestClient api;
    private String adminToken;
    private String doctorToken;
    private String patientToken;
    private long appointmentId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        adminToken = loginAs(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    @Test
    void fullLifecycle_fromBookingToInvoice_accrossTheThreeRoles() throws Exception {
        var specializationId = specializationIdOf("Cardiologia");
        var doctorId = json(api.postAs(adminToken, "/api/v1/doctors", new CreateDoctorRequest(DOCTOR_EMAIL, ApiTestClient.PASSWORD, "Giulia", "Verdi", specializationId, true))
                .andExpect(status().isOk())).get("id").asLong();

        doctorToken = api.login(DOCTOR_EMAIL);
        var startTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        var slotId = json(api.postAs(doctorToken, "/api/v1/schedule/slot", new SlotRequest(null, startTime, 30))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(doctorId))).get("id").asLong();

        // patient
        patientToken = api.registerPatient(PATIENT_EMAIL);
        api.getAs(patientToken, "/api/v1/schedule/availability?specializationId=" + specializationId+ "&date="+startTime.toLocalDate())
        .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + slotId + ")]").isNotEmpty());
        appointmentId = json(api.postAs(patientToken, "/api/v1/appointments", new CreateAppointmentRequest(slotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))).get("id").asLong();

        // doc complete visit
        api.postAs(doctorToken, "/api/v1/appointments/"+appointmentId+"/complete", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        api.postAs(doctorToken, "/api/v1/medical-reports", new CreateMedicalReportRequest(appointmentId, DIAGNOSIS, null))
                .andExpect(status().isOk());

        // patient sees the visit invoiced and reads the report
        api.getAs(patientToken, "/api/v1/appointments/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("INVOICED"));
        api.getAs(patientToken, "/api/v1/medical-reports?appointmentId="+appointmentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosisText").value(DIAGNOSIS));

        // admin: the revenue of the visit is in the dashboard, and the whole story is in the access log
        api.getAs(adminToken, "/api/v1/dashboard/kpi")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAppointments").value(1))
                .andExpect(jsonPath("$.appointmentsByStatus.INVOICED").value(1))
                .andExpect(jsonPath("$.totalRevenue").value(100.0));
        var log = api.getAs(adminToken, "/api/v1/audit-logs?limit=200").andExpect(status().isOk());
        for (var action : new String[]{"BOOK_APPOINTMENT", "COMPLETE_VISIT", "WRITE_REPORT", "ISSUE_INVOICE", "READ_REPORT"}) {
            log.andExpect(jsonPath("$[?(@.action == '" + action + "' && @.outcome == 'SUCCESS')]").isNotEmpty());
        }
    }

    @Test
    void caregiver_readsTheReportsThroughTheLink_untilThePatientRevokesIt() throws Exception {
        invoicedVisit();

        var delegation = json(api.postAs(patientToken, "/api/v1/report-delegations",
                        new CreateReportDelegationRequest("Luigi Neri", "luigi.neri@example.com", null))
                .andExpect(status().isOk()));
        var token = delegation.get("token").asString();

        // The caregiver has no account and sends no Authorization header
        mockMvc.perform(get("/api/v1/report-delegations/" + token + "/medical-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].diagnosisText").value(DIAGNOSIS))
                .andExpect(jsonPath("$[0].doctorLastName").value("Verdi"));

        api.postAs(patientToken, "/api/v1/report-delegations/" + delegation.get("id").asLong() + "/revoke", null)
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/report-delegations/" + token + "/medical-reports"))
                .andExpect(status().isNotFound());

        // Both the access and the refused attempt are in the log
        api.getAs(adminToken, "/api/v1/audit-logs?limit=200")
                .andExpect(jsonPath("$[?(@.action == 'ACCESS_REPORTS_VIA_DELEGATION' && @.outcome == 'SUCCESS')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action == 'ACCESS_REPORTS_VIA_DELEGATION' && @.outcome == 'FAILURE')]").isNotEmpty());
    }

    @Test
    void erasure_removesTheIdentity_butKeepsTheRevenueAndTheClinicalRecord() throws Exception {
        invoicedVisit();
        var token = json(api.postAs(patientToken, "/api/v1/report-delegations",
                        new CreateReportDelegationRequest("Luigi Neri", "luigi.neri@example.com", null))
                .andExpect(status().isOk())).get("token").asString();

        api.getAs(patientToken, "/api/v1/patients/me/export")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value(PATIENT_EMAIL))
                .andExpect(jsonPath("$.appointments.length()").value(1))
                .andExpect(jsonPath("$.reports.length()").value(1));

        api.postAs(patientToken, "/api/v1/patients/me/anonymize", null).andExpect(status().isNoContent());

        // The account is dead (not active), the caregiver link too
        api.getAs(patientToken, "/api/v1/patients/me/export").andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/report-delegations/" + token + "/medical-reports")).andExpect(status().isNotFound());

        // The doctor still sees the visit, now without a name; the money stays in the dashboard
        api.getAs(doctorToken, "/api/v1/appointments/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("INVOICED"))
                .andExpect(jsonPath("$[0].patientLastName").value("Anonimizzato"));
        api.getAs(adminToken, "/api/v1/dashboard/kpi")
                .andExpect(jsonPath("$.totalRevenue").value(100.0));
    }

    private void invoicedVisit() throws Exception {
        var specId = specializationIdOf("Cardiologia");
        api.postAs(adminToken, "/api/v1/doctors", new CreateDoctorRequest(DOCTOR_EMAIL, ApiTestClient.PASSWORD, "Giulia", "Verdi",  specId, true))
                .andExpect(status().isOk());
        doctorToken = api.login(DOCTOR_EMAIL);
        var startTime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        var slotId = json(api.postAs(doctorToken, "/api/v1/schedule/slot", new SlotRequest(null, startTime, 30))
                .andExpect(status().isOk())).get("id").asLong();
        patientToken = api.registerPatient(PATIENT_EMAIL);
        appointmentId = api.book(patientToken, slotId);
        api.postAs(doctorToken, "/api/v1/appointments/"+appointmentId+"/complete", null).andExpect(status().isOk());
        api.postAs(doctorToken, "/api/v1/medical-reports", new CreateMedicalReportRequest(appointmentId, DIAGNOSIS, null))
                .andExpect(status().isOk());
    }

    private long specializationIdOf(String name) throws Exception {
        var specs = json(api.getAs(adminToken, "/api/v1/specializations")
                .andExpect(status().isOk()));
        for(var specialization : specs){
            if(name.equals(specialization.get("name").asString())){
                return specialization.get("id").asLong();
            }
        }
        throw new IllegalArgumentException("Specialization with name " + name + " not found");
    }

    private String loginAs(String adminEmail, String adminPassword) throws Exception {
        var response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(adminEmail, adminPassword))))
                .andExpect(status().isOk());
        return json(response).get("token").asString();
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }


}
