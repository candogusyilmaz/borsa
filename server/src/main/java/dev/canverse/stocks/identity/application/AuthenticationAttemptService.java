package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.application.model.LocalAuthenticationResult;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.web.request.LocalRefreshRequest;
import dev.canverse.stocks.identity.web.request.RefreshTokenDelivery;
import dev.canverse.stocks.platform.application.SecurityEventRecorder;
import dev.canverse.stocks.platform.error.AppException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationAttemptService {

    private final LocalAccountRegistrationService registrationService;
    private final LocalLoginService localLoginService;
    private final RefreshSessionRotationService refreshSessionRotationService;
    private final AuthenticationAbuseProtection abuseProtection;
    private final SecurityEventRecorder securityEventRecorder;

    public UUID attemptRegistration(String email, String password, String remoteAddr, String traceId) {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(traceId, "traceId");

        var attemptResult = abuseProtection.consumeRegistrationAttempt(remoteAddr);
        if (attemptResult.status() == AuthenticationAbuseProtection.AttemptStatus.JUST_BLOCKED) {
            try {
                securityEventRecorder.recordAnonymousRequiresNew(SecurityEventRecorder.REGISTRATION_THROTTLED,
                        Map.of("traceId", traceId, "operation", "REGISTER"));
            } catch (RuntimeException exception) {
                abuseProtection.rollbackThrottle(attemptResult.transition());
                throw exception;
            }
            throw new AppException(IdentityErrorCode.AUTHENTICATION_THROTTLED);
        }
        if (attemptResult.status() == AuthenticationAbuseProtection.AttemptStatus.BLOCKED) {
            throw new AppException(IdentityErrorCode.AUTHENTICATION_THROTTLED);
        }

        return registrationService.register(email, password);
    }

    public LocalAuthenticationResult attemptLogin(String email, String password, String deviceLabel, String remoteAddr, String traceId) {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(traceId, "traceId");

        if (abuseProtection.checkLoginAllowed(email, remoteAddr) == AuthenticationAbuseProtection.CheckResult.BLOCKED) {
            throw new AppException(IdentityErrorCode.AUTHENTICATION_THROTTLED);
        }

        try {
            var result = localLoginService.login(email, password, deviceLabel);
            abuseProtection.recordLoginSuccess(email, remoteAddr);
            return result;
        } catch (AppException exception) {
            if (exception.getErrorCode() == IdentityErrorCode.INVALID_CREDENTIALS) {
                handleInvalidLogin(email, remoteAddr, traceId);
            }
            throw exception;
        }
    }

    public LocalAuthenticationResult attemptRefresh(LocalRefreshRequest request, List<String> refreshCookieValues, String remoteAddr, String traceId) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(refreshCookieValues, "refreshCookieValues");
        Objects.requireNonNull(traceId, "traceId");

        if (abuseProtection.checkRefreshAllowed(remoteAddr) == AuthenticationAbuseProtection.CheckResult.BLOCKED) {
            throw new AppException(IdentityErrorCode.AUTHENTICATION_THROTTLED);
        }

        String rawRefreshToken;
        try {
            rawRefreshToken = selectRefreshCredential(request, refreshCookieValues);
        } catch (AppException exception) {
            handleRefreshFailure(remoteAddr, traceId);
            throw exception;
        }

        var result = refreshSessionRotationService.rotate(rawRefreshToken).orElseThrow(() -> {
            handleRefreshFailure(remoteAddr, traceId);
            return new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        });

        abuseProtection.recordRefreshSuccess(remoteAddr);
        return result;
    }

    private void handleInvalidLogin(String email, String remoteAddr, String traceId) {
        securityEventRecorder.recordAnonymousRequiresNew(SecurityEventRecorder.LOCAL_LOGIN_FAILED, Map.of("traceId", traceId, "operation", "LOGIN"));

        abuseProtection.recordLoginFailure(email, remoteAddr).ifPresent(transition -> {
            try {
                securityEventRecorder.recordAnonymousRequiresNew(SecurityEventRecorder.LOCAL_LOGIN_THROTTLED, Map.of("traceId", traceId, "operation", "LOGIN"));
            } catch (RuntimeException exception) {
                abuseProtection.rollbackThrottle(transition);
                throw exception;
            }
        });
    }

    private void handleRefreshFailure(String remoteAddr, String traceId) {
        abuseProtection.recordRefreshFailure(remoteAddr).ifPresent(transition -> {
            try {
                securityEventRecorder.recordAnonymousRequiresNew(SecurityEventRecorder.REFRESH_THROTTLED, Map.of("traceId", traceId, "operation", "REFRESH"));
            } catch (RuntimeException exception) {
                abuseProtection.rollbackThrottle(transition);
                throw exception;
            }
        });
    }

    private String selectRefreshCredential(LocalRefreshRequest request, List<String> refreshCookieValues) {
        if (request.refreshTokenDelivery() == RefreshTokenDelivery.RESPONSE_BODY) {
            if (request.refreshToken() == null || request.refreshToken().isBlank() || !refreshCookieValues.isEmpty()) {
                throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
            }
            return request.refreshToken();
        }
        var refreshCookie = refreshCookieValues.size() == 1 ? refreshCookieValues.getFirst() : null;
        if (request.refreshTokenDelivery() == RefreshTokenDelivery.HTTP_ONLY_COOKIE && request.refreshToken() == null && refreshCookieValues.size() == 1 &&
                refreshCookie != null && !refreshCookie.isBlank()) {
            return refreshCookie;
        }
        throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
    }
}
