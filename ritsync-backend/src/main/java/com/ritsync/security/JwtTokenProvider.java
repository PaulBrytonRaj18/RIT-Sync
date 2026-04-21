package com.ritsync.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessExpiry;
    private final long refreshExpiry;

    private static final int MIN_SECRET_LENGTH = 32;

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
        return buildToken(principal, accessExpiry, "access");
    }

    public String generateRefreshToken(UserPrincipal principal) {
        return buildToken(principal, refreshExpiry, "refresh");
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

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }
}
