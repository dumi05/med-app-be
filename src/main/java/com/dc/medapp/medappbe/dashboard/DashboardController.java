package com.dc.medapp.medappbe.dashboard;

import com.dc.medapp.medappbe.dashboard.dto.DashboardKpiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/kpi")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DashboardKpiResponse> getKpi() {
        return ResponseEntity.ok(dashboardService.getKpi());
    }
}
