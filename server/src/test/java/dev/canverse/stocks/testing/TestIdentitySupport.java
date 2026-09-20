package dev.canverse.stocks.testing;

import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.domain.UserAccount;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class TestIdentitySupport {

    private final UserAccountRepository userAccountRepository;
    private final RefreshSessionIssuanceService refreshSessionIssuanceService;
    private final AccessTokenIssuanceService accessTokenIssuanceService;
    private final Clock clock;

    TestIdentitySupport(UserAccountRepository userAccountRepository, RefreshSessionIssuanceService refreshSessionIssuanceService,
            AccessTokenIssuanceService accessTokenIssuanceService, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.refreshSessionIssuanceService = refreshSessionIssuanceService;
        this.accessTokenIssuanceService = accessTokenIssuanceService;
        this.clock = clock;
    }

    public Identity create(String email) {
        var userId = createUserAccount(email);
        var session = refreshSessionIssuanceService.issue(userId, "integration-test");
        var accessToken = accessTokenIssuanceService.issue(session.sessionId());
        return new Identity(userId, session.sessionId(), accessToken.accessToken());
    }

    public UUID createUserAccount(String email) {
        var normalizedEmail = email.toLowerCase(Locale.ROOT);
        var user = userAccountRepository.saveAndFlush(UserAccount.register(UUID.randomUUID(), email, normalizedEmail, clock.instant()));
        return user.getId();
    }

    public record Identity(UUID userId, UUID sessionId, String accessToken) {

        public RequestPostProcessor asBearer() {
            return request -> {
                request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
                return request;
            };
        }
    }
}
