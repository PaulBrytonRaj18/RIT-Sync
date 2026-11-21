package com.ritsync.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessExpiry;
    private final long refreshExpiry;

    private static final int MIN_SECRET_LENGTH = 32;
    static final String TYPE_ACCESS  = "access";
    static final String TYPE_REFRESH = "refresh";

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms}") long accessExpiry,
            @Value("${jwt.refresh-token-expiry-ms}") long refreshExpiry) {
        validateSecret(secret);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiry  = accessExpiry;
        this.refreshExpiry = refreshExpiry;
    }

    private void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET cannot be empty. Set JWT_SECRET environment variable.");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least " + MIN_SECRET_LENGTH + " characters for secure HS256 signing. " +
                "Current length: " + secret.length() + ". Generate a secure key: openssl rand -base64 32"
            );
        }
    }

    public String generateAccessToken(UserPrincipal principal) {
        return buildToken(principal, accessExpiry, TYPE_ACCESS);
    }

    public String generateRefreshToken(UserPrincipal principal) {
        return buildToken(principal, refreshExpiry, TYPE_REFRESH);
    }

    private String buildToken(UserPrincipal p, long expiryMs, String type) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);
        return Jwts.builder()
                .subject(p.getEmail())
                .claim("userId", p.getId())
                .claim("role",   p.getRole())
                .claim("type",   type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    public String getEmailFromToken(String token) {
        return parseToken(token).getSubject();
    }

    /** Returns the subject (email) if this is a valid, unexpired ACCESS token; otherwise empty. */
    public Optional<String> validateAccessToken(String token) {
        return validateTyped(token, TYPE_ACCESS);
    }

    /** Returns the subject (email) if this is a valid, unexpired REFRESH token; otherwise empty. */
    public Optional<String> validateRefreshToken(String token) {
        return validateTyped(token, TYPE_REFRESH);
    }

    private Optional<String> validateTyped(String token, String expectedType) {
        try {
            Claims claims = parseToken(token);
            if (!expectedType.equals(claims.get("type", String.class))) {
                log.debug("Rejected JWT: expected type '{}'", expectedType);
                return Optional.empty();
            }
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
