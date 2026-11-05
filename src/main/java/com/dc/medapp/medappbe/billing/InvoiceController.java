package com.dc.medapp.medappbe.billing;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.billing.dto.InvoiceResponse;
import com.dc.medapp.medappbe.reports.AttachmentHttp;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/appointments/{appointmentId}/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService service;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<InvoiceResponse> issue(@AuthenticationPrincipal UserEntity user, @PathVariable Long appointmentId){
        return ResponseEntity.ok(InvoiceResponse.fromEntity(service.issue(user, appointmentId)));
    }

    @GetMapping
    public ResponseEntity<InvoiceResponse> get(@AuthenticationPrincipal UserEntity user, @PathVariable Long appointmentId){
        return ResponseEntity.ok(InvoiceResponse.fromEntity(service.find(user, appointmentId)));
    }

    @GetMapping("/pdf")
    public ResponseEntity<Resource> pdf(@AuthenticationPrincipal UserEntity user, @PathVariable Long appointmentId){
        return AttachmentHttp.asDownload(service.pdf(user, appointmentId));
    }
}
