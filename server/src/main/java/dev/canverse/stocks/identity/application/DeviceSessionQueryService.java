package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionReadRepository;
import dev.canverse.stocks.identity.web.response.DeviceSessionResponse;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceSessionQueryService {

    private final DeviceSessionReadRepository readRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<DeviceSessionResponse> listSessions(UUID userAccountId, UUID currentSessionId) {
        Objects.requireNonNull(userAccountId, "userAccountId");
        Objects.requireNonNull(currentSessionId, "currentSessionId");

        var observedAt = clock.instant();
        return readRepository.findFamilies(userAccountId, currentSessionId).stream().map(record -> DeviceSessionResponse.from(record, observedAt)).toList();
    }

    @Transactional(readOnly = true)
    public DeviceSessionResponse getSessionDetail(UUID userAccountId, UUID currentSessionId, UUID familyId) {
        Objects.requireNonNull(userAccountId, "userAccountId");
        Objects.requireNonNull(currentSessionId, "currentSessionId");
        Objects.requireNonNull(familyId, "familyId");

        var observedAt = clock.instant();
        var record = readRepository.findFamilyDetail(userAccountId, currentSessionId, familyId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.SESSION_NOT_FOUND));

        return DeviceSessionResponse.from(record, observedAt);
    }
}
