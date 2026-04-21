package com.ritsync.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@Order(1)
public class RateLimitingFilter implements Filter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        if (!path.startsWith("/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        String clientKey = getClientKey(httpRequest);
        Bucket bucket = buckets.computeIfAbsent(clientKey, this::createBucket);

        if (bucket.tryConsume(1)) {
            httpResponse.setHeader("X-Rate-Limit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for client: {}", clientKey);
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
        }
    }

    private String getClientKey(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            ip = xff.split(",")[0].trim();
        }
        return ip;
    }

    private Bucket createBucket(String key) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillGreedy(5, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
