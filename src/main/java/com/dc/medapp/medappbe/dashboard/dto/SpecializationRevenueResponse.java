package com.dc.medapp.medappbe.dashboard.dto;

import java.math.BigDecimal;

public record SpecializationRevenueResponse(
        String specialization,
        BigDecimal revenue
) {
}
