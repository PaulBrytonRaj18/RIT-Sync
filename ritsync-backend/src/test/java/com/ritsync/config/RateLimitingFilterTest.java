package com.ritsync.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterTest {

    private static MockHttpServletRequest request(String servletPath, String ip) {
        var req = new MockHttpServletRequest("POST", "/api" + servletPath);
        req.setContextPath("/api");
        req.setServletPath(servletPath);
        req.setRemoteAddr(ip);
        return req;
    }

    private static int call(RateLimitingFilter f, MockHttpServletRequest req) throws Exception {
        var res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void authEndpointsAreLimitedStrictlyEvenUnderContextPath() throws Exception {
        var filter = new RateLimitingFilter(3, 100);
        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, request("/auth/login", "1.1.1.1"))).isEqualTo(200);
        }
        assertThat(call(filter, request("/auth/login", "1.1.1.1"))).isEqualTo(429);
    }

    @Test
    void normalApiTrafficUsesTheGenerousTier() throws Exception {
        var filter = new RateLimitingFilter(1, 50);
        for (int i = 0; i < 50; i++) {
            assertThat(call(filter, request("/sessions/open", "2.2.2.2"))).isEqualTo(200);
        }
        assertThat(call(filter, request("/sessions/open", "2.2.2.2"))).isEqualTo(429);
    }

    @Test
    void exhaustingAuthTierDoesNotBlockTheRestOfTheApi() throws Exception {
        var filter = new RateLimitingFilter(1, 10);
        call(filter, request("/auth/login", "3.3.3.3"));
        assertThat(call(filter, request("/auth/login", "3.3.3.3"))).isEqualTo(429);
        assertThat(call(filter, request("/courses/my", "3.3.3.3"))).isEqualTo(200);
    }

    @Test
    void spoofedForwardedForHeaderDoesNotGetAFreshBucket() throws Exception {
        var filter = new RateLimitingFilter(1, 10);
        var first = request("/auth/login", "4.4.4.4");
        call(filter, first);
        var spoofed = request("/auth/login", "4.4.4.4");
        spoofed.addHeader("X-Forwarded-For", "9.9.9.9");
        assertThat(call(filter, spoofed)).isEqualTo(429);
    }

    @Test
    void healthCheckIsExempt() throws Exception {
        var filter = new RateLimitingFilter(1, 1);
        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, request("/actuator/health", "5.5.5.5"))).isEqualTo(200);
        }
    }
}
