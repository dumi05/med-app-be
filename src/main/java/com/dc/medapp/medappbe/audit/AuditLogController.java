package com.dc.medapp.medappbe.audit;

import com.dc.medapp.medappbe.audit.dto.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {
    private final AuditLogService service;

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> getLatestLogs(@RequestParam(defaultValue = "50") int limit){
        return ResponseEntity.ok(service.findLatest(limit).stream().map(AuditLogResponse::fromEntity).toList());
    }
}
