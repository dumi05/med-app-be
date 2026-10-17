package com.dc.medapp.medappbe.billing;

import java.math.BigDecimal;

public interface SpecializationRevenueView {
    String getSpecializationName();
    BigDecimal getTotalRevenue();
}
