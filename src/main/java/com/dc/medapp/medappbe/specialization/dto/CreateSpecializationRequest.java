package com.dc.medapp.medappbe.specialization.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateSpecializationRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull @DecimalMin("0.01") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2) BigDecimal price
) {
}
