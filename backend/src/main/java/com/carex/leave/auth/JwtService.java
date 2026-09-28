package com.carex.leave.auth;

import com.carex.leave.common.time.BusinessCalendar;
import com.carex.leave.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** HS256 JWT, 8h, issuer carex-leave (implementation.md §13). Algorithm pinned by the typed key. */
@Service
public class JwtService {
    public static final String ISSUER = "carex-leave";
    private final SecretKey key;
    private final AppProperties props;
    private final BusinessCalendar calendar;

    public JwtService(AppProperties props, BusinessCalendar calendar) {
        this.props = props;
        this.calendar = calendar;
        String secret = props.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be set (base64, >= 32 bytes)");
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret);
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET must be base64 encoded");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public Token issue(Long userId, String role) {
        Instant now = calendar.now();
        Instant exp = now.plus(props.jwt().ttl());
        String jwt = Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new Token(jwt, exp);
    }

    /** Returns the user id if the token is valid (signature, issuer, expiry). */
    public Optional<Long> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(calendar.now()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.parseLong(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record Token(String value, Instant expiresAt) {}
}
