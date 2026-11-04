package com.dc.medapp.medappbe.news.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateNewsRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String body,
        @NotNull LocalDate expiresAt
        ) {
}
