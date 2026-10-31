package com.dc.medapp.medappbe.security;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtServiceTest {

    private JwtService jwtService;
    private UserEntity user;

    @BeforeEach
    public void setUp() {
        jwtService = new JwtService("test-secret-key-for-testing-purposes-only-32chars", 3600000); // 1 ora exp
        user = UserEntity.builder()
                .email("testuser@example.com")
                .role(Role.PATIENT)
                .passwordHash("hashedpassword")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    public void generateToken_extractEmail_returnsCorrectEmail() {
        String token = jwtService.generateToken(user);
        String extractedEmail = jwtService.extractEmail(token);
        assertThat(extractedEmail).isEqualTo(user.getEmail());
    }

    @Test
    void isTokenValid_returnsTrue_ForMatchingUserAndToken(){
        String token = jwtService.generateToken(user);
        boolean isValid = jwtService.isTokenValid(token, user);
        assertThat(isValid).isTrue();
    }

    @Test
    void isTokenValid_returnsFalse_ForNonMatchingUserAndToken(){
        String token = jwtService.generateToken(user);
        UserEntity anotherUser = UserEntity.builder()
                .email("anotheruser@example.com")
                .role(Role.PATIENT)
                .passwordHash("anotherhashedpassword")
                .build();
        boolean isValid = jwtService.isTokenValid(token, anotherUser);
        assertThat(isValid).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_whenThePasswordWasChangedAfterTheTokenWasIssued() {
        String token = jwtService.generateToken(user);

        user.setTokenVersion(user.getTokenVersion() + 1);

        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }

    @Test
    void isTokenValid_returnsTrue_forATokenOfTheCurrentVersion() {
        user.setTokenVersion(3);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isTrue();
        user.setTokenVersion(4);
        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }

    @Test
    void isTokenValid_countsATokenWithoutTheVersionAsVersionZero() {
        // the tokens issued before the versions existed
        var key = Keys.hmacShaKeyFor("test-secret-key-for-testing-purposes-only-32chars".getBytes());
        String legacy = Jwts.builder().subject(user.getEmail()).issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();

        assertThat(jwtService.isTokenValid(legacy, user)).isTrue();
        user.setTokenVersion(1);
        assertThat(jwtService.isTokenValid(legacy, user)).isFalse();
    }
}