package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.reports.dto.CreateMedicalReportRequest;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class DelegationAuthorizationTest {

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
    private AvailabilitySlotRepository slotRepository;
    @Autowired
    private PriceListRepository priceListRepository;
    @Autowired
    private DoctorDelegationRepository delegationRepository;

    private ApiTestClient api;
    private DoctorEntity delegating;
    private DoctorEntity delegate;
    private String delegatingToken;
    private String delegateToken;
    private long appointmentId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-deleg-auth-test").build());
        priceListRepository.save(PriceListEntity.builder().specialization(specialization)
                .visitType(BillingService.STANDARD_VISIT_TYPE).price(new BigDecimal("100.00")).build());

        delegating = api.createDoctorWithConsentFlag("delegating.auth@test.it", true, specialization);
        delegate = api.createDoctorWithConsentFlag("delegate.auth@test.it", true, specialization);
        delegatingToken = api.login("delegating.auth@test.it");
        delegateToken = api.login("delegate.auth@test.it");

        var slot = slotRepository.save(AvailabilitySlotEntity.builder().doctor(delegating).booked(false)
                .durationMinutes(30).startTime(LocalDateTime.now().plusDays(2)).build());
        appointmentId = api.book(api.registerPatient("patient.auth@test.it"), slot.getId());
    }

    // test business logic
    @Test
    void doctorWithoutDelegation_cannotCompleteTheVisit_receives403() throws Exception {
        api.postAs(delegateToken, "/api/v1/appointments/" + appointmentId + "/complete", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    void delegateWithAnActiveDelegation_seesTheAppointmentsOfTheDelegatingDoctor() throws Exception {
        saveDelegation(DelegationStatus.ACCEPTED, -1, 5);

        api.getAs(delegateToken, "/api/v1/appointments/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(appointmentId));
    }

    @Test
    void doctorWithoutDelegation_doesNotSeeTheAppointmentsOfAColleague() throws Exception {
        api.getAs(delegateToken, "/api/v1/appointments/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void delegateWithAnActiveDelegation_canCompleteTheVisitAndWriteTheReport() throws Exception {
        saveDelegation(DelegationStatus.ACCEPTED, -1, 5);

        api.postAs(delegateToken, "/api/v1/appointments/" + appointmentId + "/complete", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        api.postReport(delegateToken, new CreateMedicalReportRequest(appointmentId, "No findings."), null)
                .andExpect(status().isOk());
    }

    @Test
    void delegatingDoctor_canReadTheReportWrittenByTheDelegate() throws Exception {
        saveDelegation(DelegationStatus.ACCEPTED, -1, 5);
        api.postAs(delegateToken, "/api/v1/appointments/" + appointmentId + "/complete", null).andExpect(status().isOk());
        api.postReport(delegateToken, new CreateMedicalReportRequest(appointmentId, "No findings."), null).andExpect(status().isOk());

        api.getAs(delegatingToken, "/api/v1/medical-reports?appointmentId=" + appointmentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosisText").value("No findings."));
    }

    @Test
    void delegationOutsideItsDates_cannotCompleteTheVisit_receives403() throws Exception {
        saveDelegation(DelegationStatus.ACCEPTED, -10, -1);

        api.postAs(delegateToken, "/api/v1/appointments/" + appointmentId + "/complete", null)
                .andExpect(status().isForbidden());
    }

    @Test
    void proposedButNotAcceptedDelegation_cannotCompleteTheVisit_receives403() throws Exception {
        saveDelegation(DelegationStatus.PROPOSED, -1, 5);

        api.postAs(delegateToken, "/api/v1/appointments/" + appointmentId + "/complete", null)
                .andExpect(status().isForbidden());
    }

    @Test
    void delegationIsNotReversible_theDelegatingDoctorCannotActOnTheDelegatesAppointments() throws Exception {
        saveDelegation(DelegationStatus.ACCEPTED, -1, 5);
        var slot = slotRepository.save(AvailabilitySlotEntity.builder().doctor(delegate).booked(false)
                .durationMinutes(30).startTime(LocalDateTime.now().plusDays(3)).build());
        var delegateAppointment = api.book(api.registerPatient("patient.auth2@test.it"), slot.getId());

        api.postAs(delegatingToken, "/api/v1/appointments/" + delegateAppointment + "/complete", null)
                .andExpect(status().isForbidden());
    }

    // set helpers for tests

    private void saveDelegation(DelegationStatus status, int startOffsetDays, int endOffsetDays) {
        delegationRepository.save(DoctorDelegationEntity.builder()
                .delegatingDoctor(delegating).delegateDoctor(delegate)
                .startDate(LocalDate.now().plusDays(startOffsetDays))
                .endDate(LocalDate.now().plusDays(endOffsetDays))
                .status(status).build());
    }

}