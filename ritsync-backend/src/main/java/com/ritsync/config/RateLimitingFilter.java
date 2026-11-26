package com.ritsync.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-IP rate limiting with two tiers:
 * <ul>
 *   <li><b>auth</b>  – login / register / refresh: strict, to slow credential stuffing.</li>
 *   <li><b>api</b>   – everything else: generous, only to stop runaway clients.</li>
 * </ul>
 * The client IP comes from {@link HttpServletRequest#getRemoteAddr()}. With
 * {@code server.forward-headers-strategy=native}, Tomcat rewrites it from X-Forwarded-For
 * only when the request came through a trusted internal proxy, so clients cannot spoof it.
 * <p>
 * Buckets live in memory (bounded LRU). If you run more than one backend instance,
 * move this to a shared store (e.g. bucket4j-redis) or limits become N × capacity.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitingFilter extends OncePerRequestFilter {

    static final Set<String> AUTH_PATHS = Set.of("/auth/login", "/auth/register", "/auth/refresh");
    private static final Set<String> EXEMPT_PATHS = Set.of("/actuator/health");
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final long authCapacity;
    private final long apiCapacity;

    private final Map<String, Bucket> buckets = Collections.synchronizedMap(
        new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > MAX_TRACKED_CLIENTS;
            }
        });

    public RateLimitingFilter(@Value("${app.rate-limit.auth-capacity:10}") long authCapacity,
                              @Value("${app.rate-limit.api-capacity:300}") long apiCapacity) {
        this.authCapacity = authCapacity;
        this.apiCapacity = apiCapacity;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // getServletPath() excludes the /api context path, so "/auth/login" matches as intended.
        String path = request.getServletPath();
        if (EXEMPT_PATHS.contains(path) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        boolean isAuth = AUTH_PATHS.contains(path);
        String key = (isAuth ? "auth:" : "api:") + request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(isAuth ? authCapacity : apiCapacity));

        if (bucket.tryConsume(1)) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded: tier={} ip={}", isAuth ? "auth" : "api", request.getRemoteAddr());
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Too many requests. Please try again later.\"}");
        }
    }

    private static Bucket newBucket(long capacityPerMinute) {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(capacityPerMinute)
                .refillGreedy(capacityPerMinute, Duration.ofMinutes(1))
                .build())
            .build();
    }
}
