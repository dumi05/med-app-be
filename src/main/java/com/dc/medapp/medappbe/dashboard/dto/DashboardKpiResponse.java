package com.dc.medapp.medappbe.dashboard.dto;

import com.dc.medapp.medappbe.appointments.AppointmentStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardKpiResponse(
        long totalAppointments,
        Map<AppointmentStatus, Long> appointmentsByStatus,
        long upcomingAtRisk,
        double attendanceRate,
        BigDecimal totalRevenue,
        List<SpecializationRevenueResponse> revenueBySpecialization
) {
}
