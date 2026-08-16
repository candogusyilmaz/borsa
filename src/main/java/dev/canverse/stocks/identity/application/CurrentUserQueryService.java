package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.identity.web.response.CurrentUserResponse;
import dev.canverse.stocks.platform.error.AppException;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrentUserQueryService {

    private final UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(UUID userAccountId) {
        Objects.requireNonNull(userAccountId, "userAccountId");

        var userAccount = userAccountRepository
                .findById(userAccountId)
                .filter(user -> user.getDisabledAt() == null)
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_CREDENTIALS));

        return new CurrentUserResponse(userAccount.getId(), userAccount.getEmail(), userAccount.getCreatedAt());
    }
}
