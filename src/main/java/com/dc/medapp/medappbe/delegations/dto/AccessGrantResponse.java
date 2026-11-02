package com.dc.medapp.medappbe.delegations.dto;

public record AccessGrantResponse(String accessToken, long expiresInSeconds) {
    public static AccessGrantResponse fromGrant(AccessGrant accessGrant) {
        return new AccessGrantResponse(accessGrant.accessToken(), accessGrant.expiresInSeconds());
    }
}
