package dev.canverse.stocks.identity.web;

import dev.canverse.stocks.identity.application.AuthenticationAbuseProtection;
import dev.canverse.stocks.identity.application.RefreshSessionRotationService;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.web.request.LocalRefreshRequest;
import dev.canverse.stocks.identity.web.request.RefreshTokenDelivery;
import dev.canverse.stocks.identity.web.response.LocalRefreshResponse;
import dev.canverse.stocks.platform.application.SecurityEventRecorder;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.web.CacheHeaders;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class LocalRefreshController {

    private static final String REFRESH_COOKIE_NAME = "refresh-token";

    private final RefreshSessionRotationService rotationService;
    private final AuthenticationAbuseProtection abuseProtection;
    private final SecurityEventRecorder securityEventRecorder;
    private final Clock clock;

    public LocalRefreshController(
            RefreshSessionRotationService rotationService,
            AuthenticationAbuseProtection abuseProtection,
            SecurityEventRecorder securityEventRecorder,
            Clock clock) {
        this.rotationService = rotationService;
        this.abuseProtection = abuseProtection;
        this.securityEventRecorder = securityEventRecorder;
        this.clock = clock;
    }

    @PostMapping(value = "refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LocalRefreshResponse> refresh(
            @Valid @RequestBody LocalRefreshRequest request, HttpServletRequest servletRequest) {
        var remoteAddr = servletRequest.getRemoteAddr();
        var traceIdAttribute = (String) servletRequest.getAttribute(RequestTraceFilter.TRACE_ID_ATTRIBUTE);
        var traceId = traceIdAttribute == null ? "unknown" : traceIdAttribute;

        if (abuseProtection.checkRefreshAllowed(remoteAddr) == AuthenticationAbuseProtection.CheckResult.BLOCKED) {
            throw new AppException(IdentityErrorCode.AUTHENTICATION_THROTTLED);
        }

        String rawRefreshToken;
        try {
            rawRefreshToken = selectCredential(request, servletRequest);
        } catch (AppException exception) {
            handleRefreshFailure(remoteAddr, traceId);
            throw exception;
        }

        var result = rotationService.rotate(rawRefreshToken).orElseThrow(() -> {
            handleRefreshFailure(remoteAddr, traceId);
            return new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        });

        abuseProtection.recordRefreshSuccess(remoteAddr);
        var serverTime = clock.instant();
        var responseRefreshToken =
                request.refreshTokenDelivery() == RefreshTokenDelivery.RESPONSE_BODY ? result.refreshToken() : null;
        var response = LocalRefreshResponse.from(result, serverTime, responseRefreshToken);

        var headers = CacheHeaders.noStore();
        if (request.refreshTokenDelivery() == RefreshTokenDelivery.HTTP_ONLY_COOKIE) {
            headers.add(
                    HttpHeaders.SET_COOKIE,
                    RefreshTokenCookieHeader.create(result.refreshToken(), result.refreshTokenExpiresAt(), serverTime));
        }
        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    private void handleRefreshFailure(String remoteAddr, String traceId) {
        abuseProtection.recordRefreshFailure(remoteAddr).ifPresent(transition -> {
            try {
                securityEventRecorder.recordAnonymousRequiresNew(
                        SecurityEventRecorder.REFRESH_THROTTLED, Map.of("traceId", traceId, "operation", "REFRESH"));
            } catch (RuntimeException exception) {
                abuseProtection.rollbackThrottle(transition);
                throw exception;
            }
        });
    }

    private String selectCredential(LocalRefreshRequest request, HttpServletRequest servletRequest) {
        var cookieValues = refreshCookieValues(servletRequest);
        if (request.refreshTokenDelivery() == RefreshTokenDelivery.RESPONSE_BODY) {
            if (!isNonBlank(request.refreshToken()) || !cookieValues.isEmpty()) {
                throw invalidCredentials();
            }
            return request.refreshToken();
        }
        if (request.refreshTokenDelivery() == RefreshTokenDelivery.HTTP_ONLY_COOKIE
                && request.refreshToken() == null
                && cookieValues.size() == 1
                && isNonBlank(cookieValues.getFirst())) {
            return cookieValues.getFirst();
        }
        throw invalidCredentials();
    }

    private List<String> refreshCookieValues(HttpServletRequest servletRequest) {
        var cookies = servletRequest.getCookies();
        if (cookies == null) {
            return List.of();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> Objects.equals(REFRESH_COOKIE_NAME, cookie.getName()))
                .map(Cookie::getValue)
                .toList();
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private AppException invalidCredentials() {
        return new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
    }
}
