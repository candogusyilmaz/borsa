package dev.canverse.stocks.identity.web;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseCookie;

public final class RefreshTokenCookieHeader {

    private static final String REFRESH_COOKIE_NAME = "refresh-token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final Pattern COOKIE_EXPIRES_ATTRIBUTE = Pattern.compile("(?i)Expires=[^;]+");

    private RefreshTokenCookieHeader() {}

    public static String create(String rawRefreshToken, Instant refreshTokenExpiresAt, Instant serverTime) {
        var maxAgeSeconds = Duration.between(serverTime, refreshTokenExpiresAt).toSeconds();
        if (maxAgeSeconds <= 0) {
            throw new IllegalArgumentException("Refresh cookie expiry must be in the future");
        }
        var refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
                .path(REFRESH_COOKIE_PATH)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .maxAge(maxAgeSeconds)
                .build();
        var expires = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                refreshTokenExpiresAt.truncatedTo(ChronoUnit.SECONDS).atZone(ZoneOffset.UTC));
        return COOKIE_EXPIRES_ATTRIBUTE
                .matcher(refreshCookie.toString())
                .replaceFirst(Matcher.quoteReplacement("Expires=" + expires));
    }
}
