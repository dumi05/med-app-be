package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.reports.dto.AttachmentDownload;
import com.dc.medapp.medappbe.reports.dto.CreateMedicalReportRequest;
import com.dc.medapp.medappbe.reports.dto.MedicalReportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/medical-reports")
@RequiredArgsConstructor
public class MedicalReportController {

    private final MedicalReportService medicalReportService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<MedicalReportResponse> create (@AuthenticationPrincipal UserEntity user,
                                                         @Valid @RequestPart("report") CreateMedicalReportRequest req,
                                                         @RequestPart(value = "file", required = false) MultipartFile file){
        var report = medicalReportService.createReport(user, req.appointmentId(), req.diagnosisText(), file);
        return ResponseEntity.ok(MedicalReportResponse.fromEntity(report));
    }

    @GetMapping("/{id}/attachment")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public ResponseEntity<Resource> getAttachment(@AuthenticationPrincipal UserEntity user,
                                                  @PathVariable Long id) {
        return AttachmentHttp.asDownload(medicalReportService.getAttachment(user, id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public ResponseEntity<MedicalReportResponse> getById(@AuthenticationPrincipal UserEntity user,
                                                         @PathVariable Long id){
        return ResponseEntity.ok(MedicalReportResponse.fromEntity(medicalReportService.getReport(user, id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<MedicalReportResponse> getByAppointment(@AuthenticationPrincipal UserEntity user,
                                                                  @RequestParam Long appointmentId){
        return ResponseEntity.ok(MedicalReportResponse.fromEntity(medicalReportService.getReportByAppointment(user, appointmentId)));
    }
}
