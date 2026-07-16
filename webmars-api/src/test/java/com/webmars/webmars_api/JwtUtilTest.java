package com.webmars.webmars_api;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Enhancement Plan §9.1 — JwtUtil scenarios. Plain unit tests (no
 * Spring context, no database) so they run anywhere, including
 * machines without Docker.
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-that-is-definitely-32+-bytes";

    private final JwtUtil jwt = new JwtUtil(SECRET);

    @Test
    void roundTrip_extractsTheSubject() {
        String token = jwt.generateToken("alice");
        assertThat(jwt.isTokenValid(token)).isTrue();
        assertThat(jwt.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void tamperedToken_isRejected() {
        String token = jwt.generateToken("alice");
        String[] parts = token.split("\\.");
        // Flip a character in the payload — signature no longer matches.
        String tamperedPayload = parts[1].charAt(0) == 'A'
                ? 'B' + parts[1].substring(1)
                : 'A' + parts[1].substring(1);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];
        assertThat(jwt.isTokenValid(tampered)).isFalse();
    }

    @Test
    void expiredToken_isRejected() {
        // Craft a token with the SAME secret but an expiry in the past.
        String expired = Jwts.builder()
                .subject("alice")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
        assertThat(jwt.isTokenValid(expired)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecret_isRejected() {
        JwtUtil other = new JwtUtil("a-completely-different-secret-that-is-32+-bytes");
        String foreign = other.generateToken("alice");
        assertThat(jwt.isTokenValid(foreign)).isFalse();
    }

    @Test
    void tooShortSecret_throwsAtConstruction() {
        assertThatThrownBy(() -> new JwtUtil("short-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
