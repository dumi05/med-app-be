package com.dc.medapp.medappbe.dashboard;

import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.billing.InvoiceItemRepository;
import com.dc.medapp.medappbe.dashboard.dto.DashboardKpiResponse;
import com.dc.medapp.medappbe.dashboard.dto.SpecializationRevenueResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AppointmentRepository appointmentRepo;
    private final InvoiceItemRepository invoiceItemRepo;

    /**
     * This method returns the kpi of appointments and their revenue.
     * @return {@link DashboardKpiResponse}
     */
    @Transactional(readOnly = true)
    public DashboardKpiResponse getKpi(){
        var now = LocalDateTime.now();

        var byStatus = new EnumMap<AppointmentStatus, Long>(AppointmentStatus.class);
        for (AppointmentStatus status : AppointmentStatus.values()) {
            byStatus.put(status, appointmentRepo.countByStatus(status));
        }

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        long held = byStatus.get(AppointmentStatus.COMPLETED) + byStatus.get(AppointmentStatus.REPORTED) + byStatus.get(AppointmentStatus.INVOICED);
        long missed = appointmentRepo.countByStatusAndSlotStartTimeBefore(AppointmentStatus.BOOKED, now);
        double attendanceRate = held + missed == 0 ? 0.0 : (double) held / (held+missed);

        long upcomingAtRisk = appointmentRepo.countByStatusAndNoShowRiskTrueAndSlotStartTimeAfter(AppointmentStatus.BOOKED, now);

        var totalRevenue = invoiceItemRepo.getTotalAmount();
        var revenueBySpecialization = invoiceItemRepo.sumAmountBySpecialization()
                .stream()
                .map(v -> new SpecializationRevenueResponse(v.getSpecializationName(), v.getTotalRevenue()))
                .toList();

        return new DashboardKpiResponse(total, byStatus, upcomingAtRisk, attendanceRate, totalRevenue == null ? BigDecimal.ZERO : totalRevenue, revenueBySpecialization);
    }
}
