package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.reports.dto.CreateMedicalReportRequest;
import com.dc.medapp.medappbe.reports.dto.MedicalReportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/medical-reports")
@RequiredArgsConstructor
public class MedicalReportController {

    private final MedicalReportService medicalReportService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<MedicalReportResponse> create (@AuthenticationPrincipal UserEntity user,
                                                         @Valid @RequestBody CreateMedicalReportRequest req){
        var report = medicalReportService.createReport(user, req.appointmentId(), req.diagnosisText(), req.attachmentUrl());
        return ResponseEntity.ok(MedicalReportResponse.fromEntity(report));
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
