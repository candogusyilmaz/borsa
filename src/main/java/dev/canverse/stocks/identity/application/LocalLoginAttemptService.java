package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.application.model.LocalLoginResult;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.platform.application.SecurityEventRecorder;
import dev.canverse.stocks.platform.error.AppException;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalLoginAttemptService {

    private final LocalLoginService localLoginService;
    private final AuthenticationAbuseProtection abuseProtection;
    private final SecurityEventRecorder securityEventRecorder;

    public LocalLoginResult attemptLogin(
            String email, String password, String deviceLabel, String remoteAddr, String traceId) {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(traceId, "traceId");

        var checkResult = abuseProtection.checkLoginAllowed(email, remoteAddr);
        if (checkResult == AuthenticationAbuseProtection.CheckResult.BLOCKED) {
            throw new AppException(IdentityErrorCode.AUTHENTICATION_THROTTLED);
        }

        try {
            var result = localLoginService.login(email, password, deviceLabel);
            abuseProtection.recordLoginSuccess(email, remoteAddr);
            return result;
        } catch (AppException exception) {
            if (exception.getErrorCode() == IdentityErrorCode.INVALID_CREDENTIALS) {
                handleInvalidCredentials(email, remoteAddr, traceId);
            }
            throw exception;
        }
    }

    private void handleInvalidCredentials(String email, String remoteAddr, String traceId) {
        securityEventRecorder.recordAnonymousRequiresNew(
                SecurityEventRecorder.LOCAL_LOGIN_FAILED, Map.of("traceId", traceId, "operation", "LOGIN"));

        abuseProtection.recordLoginFailure(email, remoteAddr).ifPresent(transition -> {
            try {
                securityEventRecorder.recordAnonymousRequiresNew(
                        SecurityEventRecorder.LOCAL_LOGIN_THROTTLED, Map.of("traceId", traceId, "operation", "LOGIN"));
            } catch (RuntimeException exception) {
                abuseProtection.rollbackThrottle(transition);
                throw exception;
            }
        });
    }
}
