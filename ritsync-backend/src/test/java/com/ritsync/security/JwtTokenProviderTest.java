package com.ritsync.security;

import com.ritsync.entity.User;
import com.ritsync.enums.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000, 120_000);

    private static UserPrincipal principal() {
        User u = User.builder().id(7L).email("s@ritsync.test").passwordHash("x")
            .role(Role.STUDENT).isActive(true).build();
        return new UserPrincipal(u);
    }

    @Test
    void accessTokenIsAcceptedOnlyAsAccessToken() {
        String access = provider.generateAccessToken(principal());
        assertThat(provider.validateAccessToken(access)).contains("s@ritsync.test");
        assertThat(provider.validateRefreshToken(access)).isEmpty();
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        String refresh = provider.generateRefreshToken(principal());
        assertThat(provider.validateRefreshToken(refresh)).contains("s@ritsync.test");
        assertThat(provider.validateAccessToken(refresh)).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        var other = new JwtTokenProvider("another-secret-another-secret-0123456789", 60_000, 120_000);
        String forged = other.generateAccessToken(principal());
        assertThat(provider.validateAccessToken(forged)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        var shortLived = new JwtTokenProvider(SECRET, -1_000, -1_000);
        assertThat(provider.validateAccessToken(shortLived.generateAccessToken(principal()))).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(provider.validateAccessToken("not-a-jwt")).isEmpty();
    }

    @Test
    void shortOrMissingSecretFailsFast() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short", 1, 1))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenProvider("", 1, 1))
            .isInstanceOf(IllegalStateException.class);
    }
}
