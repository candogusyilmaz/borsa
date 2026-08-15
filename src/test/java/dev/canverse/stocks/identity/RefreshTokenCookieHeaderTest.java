package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.web.RefreshTokenCookieHeader;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenCookieHeaderTest {

    @Test
    void createEmitsExactAttributes() {
        var serverTime = Instant.parse("2026-08-15T12:00:00Z");
        var expiresAt = Instant.parse("2026-08-15T14:00:00Z");
        var cookie = RefreshTokenCookieHeader.create("token-val-123", expiresAt, serverTime);

        assertThat(cookie).contains("refresh-token=token-val-123");
        assertThat(cookie).contains("Path=/api/v1/auth");
        assertThat(cookie).contains("Max-Age=7200");
        assertThat(cookie).contains("Expires=Sat, 15 Aug 2026 14:00:00 GMT");
        assertThat(cookie).contains("Secure");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("SameSite=Strict");
        assertThat(cookie).doesNotContain("Domain=");
    }

    @Test
    void clearEmitsExactAttributes() {
        var cookie = RefreshTokenCookieHeader.clear();

        assertThat(cookie).contains("refresh-token=");
        assertThat(cookie).contains("Path=/api/v1/auth");
        assertThat(cookie).contains("Max-Age=0");
        assertThat(cookie).contains("Expires=Thu, 01 Jan 1970 00:00:00 GMT");
        assertThat(cookie).contains("Secure");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("SameSite=Strict");
        assertThat(cookie).doesNotContain("Domain=");
    }

    @Test
    void createRejectsPastOrEqualExpiry() {
        var serverTime = Instant.parse("2026-08-15T12:00:00Z");
        assertThatThrownBy(() -> RefreshTokenCookieHeader.create("token", serverTime, serverTime))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshTokenCookieHeader.create("token", serverTime.minusSeconds(10), serverTime))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
