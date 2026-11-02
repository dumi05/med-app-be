package com.dc.medapp.medappbe.delegations.dto;

public record AccessGrant(String accessToken, long expiresInSeconds) {
}
