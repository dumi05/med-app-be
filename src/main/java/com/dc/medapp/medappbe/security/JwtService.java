package com.dc.medapp.medappbe.security;

import com.dc.medapp.medappbe.auth.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private static final String TYPE_CLAIM = "typ";
    private static final String DELEGATION_ACCESS = "delegation-access";
    private final long expirationTime;

    public JwtService(@Value("${jwt.secret}") String secretKey, @Value("${jwt.expiration-ms}") long expirationTime) {
        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes());
        this.expirationTime = expirationTime;
    }

    public String generateToken(UserEntity user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .claim("ver", user.getTokenVersion())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String generateDelegationAccessToken(Long delegationId, java.time.Duration duration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + duration.toMillis());

        return Jwts.builder()
                .subject(String.valueOf(delegationId))
                .claim(TYPE_CLAIM, DELEGATION_ACCESS)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public Long delegationIdOf(String token) {
        Claims claims = getAllClaims(token);
        if (!DELEGATION_ACCESS.equals(claims.get(TYPE_CLAIM))) {
            throw new io.jsonwebtoken.JwtException("Not a delegation access token");
        }
        return Long.valueOf(claims.getSubject());
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserEntity user) {
        final String email = extractEmail(token);
        return (email.equals(user.getEmail())) && !isTokenExpired(token) && !isDelegationAccessToken(token) && tokenVersionOf(token) == user.getTokenVersion();
    }

    private boolean isDelegationAccessToken(String token) {
        return DELEGATION_ACCESS.equals(extractClaim(token, claims -> claims.get(TYPE_CLAIM)));
    }

    private int tokenVersionOf(String token) {
        Integer version = extractClaim(token, claims -> claims.get("ver", Integer.class));
        return version == null ? 0 : version;
    }

    public boolean isTokenExpired(String token) {
        final Date expiration = extractClaim(token, Claims::getExpiration);
        return expiration.before(new Date());
    }

    private <T> T extractClaim(String token, java.util.function.Function<Claims, T> claimResolver) {
        final Claims claims = getAllClaims(token);
        return claimResolver.apply(claims);
    }

    private Claims getAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
