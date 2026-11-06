package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.reports.dto.CreateMedicalReportRequest;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import com.dc.medapp.medappbe.support.ApiTestClient;
import com.dc.medapp.medappbe.support.TestFiles;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class MedicalReportAttachmentControllerTest {

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
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private AvailabilitySlotRepository slotRepository;
    @Autowired
    private PriceListRepository priceListRepository;

    private ApiTestClient api;
    private String doctorToken;
    private String patientToken;
    private long appointmentId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper, userRepository, passwordEncoder, doctorRepository);
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-att-controller-test").build());
        priceListRepository.save(PriceListEntity.builder()
                .specialization(specialization).visitType(BillingService.STANDARD_VISIT_TYPE).price(new BigDecimal("100.00")).build());
        var doctor = api.createDoctorWithConsentFlag("att.controller.doctor@test.it", false, specialization);
        doctorToken = api.login("att.controller.doctor@test.it");
        patientToken = api.registerPatient("att.controller.patient@test.it");
        var slot = slotRepository.save(AvailabilitySlotEntity.builder()
                .doctor(doctor).startTime(LocalDateTime.now().plusDays(1)).durationMinutes(30).booked(false).build());
        appointmentId = api.book(patientToken, slot.getId());
        api.postAs(doctorToken, "/api/v1/appointments/" + appointmentId + "/complete", null).andExpect(status().isOk());
    }

    private long uploadPdf() throws Exception {
        var json = api.postReport(doctorToken, new CreateMedicalReportRequest(appointmentId, "Nessun risultato."), TestFiles.pdf())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    // ---- upload ---------------------------------------------------------------------------------

    @Test
    void doctor_canWriteTheReportWithAPdf_andTheResponseDescribesIt() throws Exception {
        api.postReport(doctorToken, new CreateMedicalReportRequest(appointmentId, "Nessun risultato."), TestFiles.pdf())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachment.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.attachment.sizeBytes").value(TestFiles.pdfBytes().length))
                .andExpect(jsonPath("$.attachmentUrl").doesNotExist());
    }

    @Test
    void doctor_canWriteTheReportWithoutAFile() throws Exception {
        api.postReport(doctorToken, new CreateMedicalReportRequest(appointmentId, "Nessun risultato."), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachment").doesNotExist());
    }

    @Test
    void aTextFileCalledPdf_returns400() throws Exception {
        api.postReport(doctorToken, new CreateMedicalReportRequest(appointmentId, "Nessun risultato."), TestFiles.fakePdf())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void anEmptyFile_returns400() throws Exception {
        api.postReport(doctorToken, new CreateMedicalReportRequest(appointmentId, "Nessun risultato."), TestFiles.empty())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void aFileOverTenMegabytes_returns413() throws Exception {
        api.postReport(doctorToken, new CreateMedicalReportRequest(appointmentId, "Nessun risultato."), TestFiles.tooLargePdf())
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void aJsonBodyIsNotAcceptedAnymore_returns415() throws Exception {
        api.postAs(doctorToken, "/api/v1/medical-reports", new CreateMedicalReportRequest(appointmentId, "Nessun risultato."))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void patient_cannotWriteAReport_returns403() throws Exception {
        api.postReport(patientToken, new CreateMedicalReportRequest(appointmentId, "Nessun risultato."), TestFiles.pdf())
                .andExpect(status().isForbidden());
    }

    // ---- download -------------------------------------------------------------------------------

    @Test
    void patient_downloadsTheSameBytes_withTheHeadersOfADownload() throws Exception {
        var reportId = uploadPdf();

        var body = api.getAs(patientToken, "/api/v1/medical-reports/" + reportId + "/attachment")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("referto-" + reportId + ".pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).isEqualTo(TestFiles.pdfBytes());
    }

    @Test
    void theFileIsNeverNamedAfterWhatTheDoctorTyped() throws Exception {
        var reportId = uploadPdf();

        api.getAs(patientToken, "/api/v1/medical-reports/" + reportId + "/attachment")
                .andExpect(header().string("Content-Disposition", not(containsString("il mio referto"))));
    }

    @Test
    void assignedDoctor_canDownloadToo() throws Exception {
        var reportId = uploadPdf();

        api.getAs(doctorToken, "/api/v1/medical-reports/" + reportId + "/attachment")
                .andExpect(status().isOk())
                .andExpect(content().bytes(TestFiles.pdfBytes()));
    }

    @Test
    void anotherPatient_cannotDownload_returns403() throws Exception {
        var reportId = uploadPdf();
        var otherToken = api.registerPatient("att.controller.other@test.it");

        api.getAs(otherToken, "/api/v1/medical-reports/" + reportId + "/attachment")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    void admin_cannotDownload_returns403() throws Exception {
        var reportId = uploadPdf();
        api.createUser("att.controller.admin@test.it", Role.ADMIN);

        api.getAs(api.login("att.controller.admin@test.it"), "/api/v1/medical-reports/" + reportId + "/attachment")
                .andExpect(status().isForbidden());
    }

    @Test
    void withoutToken_returns401() throws Exception {
        var reportId = uploadPdf();

        mockMvc.perform(get("/api/v1/medical-reports/" + reportId + "/attachment")).andExpect(status().isUnauthorized());
    }

    @Test
    void aReportWithoutAttachment_returns404() throws Exception {
        var json = api.postReport(doctorToken, new CreateMedicalReportRequest(appointmentId, "Nessun risultato."), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var reportId = objectMapper.readTree(json).get("id").asLong();

        api.getAs(patientToken, "/api/v1/medical-reports/" + reportId + "/attachment")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void anUnknownReport_returns404() throws Exception {
        api.getAs(patientToken, "/api/v1/medical-reports/999999/attachment").andExpect(status().isNotFound());
    }

    @Test
    void theReportRead_describesTheAttachment_butDoesNotCarryIt() throws Exception {
        var reportId = uploadPdf();

        api.getAs(patientToken, "/api/v1/medical-reports/" + reportId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachment.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.attachment.sizeBytes").value(TestFiles.pdfBytes().length));
    }

    @Test
    void aWordDocument_isUploaded_andDownloadedAsAnAttachment_neverShownInTheBrowser() throws Exception {
        var json = api.postReport(doctorToken, new CreateMedicalReportRequest(appointmentId, "Nessun risultato."), TestFiles.docx())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachment.contentType").value("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andReturn().getResponse().getContentAsString();
        var reportId = objectMapper.readTree(json).get("id").asLong();

        var body = api.getAs(patientToken, "/api/v1/medical-reports/" + reportId + "/attachment")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("referto-" + reportId + ".docx")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(TestFiles.docxBytes());
    }

    @Test
    void aSpreadsheetCalledDocx_aWordDocumentWithMacros_andABrokenZip_return400() throws Exception {
        for (var file : new org.springframework.mock.web.MockMultipartFile[]{TestFiles.spreadsheetCalledDocx(), TestFiles.docxWithMacros(), TestFiles.brokenZipCalledDocx()}) {
            api.postReport(doctorToken, new CreateMedicalReportRequest(appointmentId, "Nessun risultato."), file)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        }
    }
}