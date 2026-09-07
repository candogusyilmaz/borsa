package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.LocalLoginService;
import dev.canverse.stocks.identity.application.LocalPasswordAuthenticationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.application.model.IssuedAccessToken;
import dev.canverse.stocks.identity.application.model.IssuedRefreshSession;
import dev.canverse.stocks.platform.application.SecurityEventRecorder;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalLoginServiceFamilyIdTest {

    @Test
    void recordsTheRefreshSessionFamilyIdRatherThanTheSessionId() {
        var passwordAuthenticationService = mock(LocalPasswordAuthenticationService.class);
        var refreshSessionIssuanceService = mock(RefreshSessionIssuanceService.class);
        var accessTokenIssuanceService = mock(AccessTokenIssuanceService.class);
        var securityEventRecorder = mock(SecurityEventRecorder.class);
        var loginService = new LocalLoginService(passwordAuthenticationService, refreshSessionIssuanceService, accessTokenIssuanceService,
                securityEventRecorder);

        var userAccountId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        var familyId = UUID.randomUUID();
        var accessTokenExpiresAt = Instant.parse("2026-08-16T14:00:00Z");
        var refreshTokenExpiresAt = Instant.parse("2026-08-16T15:00:00Z");
        when(passwordAuthenticationService.authenticate("alice@example.com", "password")).thenReturn(userAccountId);
        when(refreshSessionIssuanceService.issue(userAccountId, "laptop"))
                .thenReturn(new IssuedRefreshSession(sessionId, familyId, "refresh-token", refreshTokenExpiresAt));
        when(accessTokenIssuanceService.issue(sessionId)).thenReturn(new IssuedAccessToken("access-token", accessTokenExpiresAt));

        var result = loginService.login("alice@example.com", "password", "laptop");

        assertThat(result.sessionId()).isEqualTo(sessionId);
        verify(securityEventRecorder).record(userAccountId, SecurityEventRecorder.LOCAL_LOGIN_SUCCEEDED,
                Map.of("sessionId", sessionId.toString(), "familyId", familyId.toString()));
    }
}
