package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.platform.application.SecurityEventRecorder;
import dev.canverse.stocks.platform.error.AppException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalRegistrationAttemptService {

    private final LocalAccountRegistrationService registrationService;
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
}
