package com.dc.medapp.medappbe.news.dto;

import com.dc.medapp.medappbe.news.NewsEntity;

import java.time.Instant;
import java.time.LocalDate;

public record NewsResponse(
        Long id,
        String title,
        String body,
        String authorFirstName,
        String authorLastName,
        Instant createdAt,
        LocalDate expiresAt,
        boolean automatic
) {
    public static NewsResponse fromEntity(NewsEntity entity){
        return new NewsResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getAuthor().getFirstName(),
                entity.getAuthor().getLastName(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.isAutomatic()
        );
    }
}
