package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.application.model.SessionCursor;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionReadRepository;
import dev.canverse.stocks.identity.web.response.DeviceSessionPageResponse;
import dev.canverse.stocks.identity.web.response.DeviceSessionResponse;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceSessionQueryService {

    public static final int DEFAULT_LIMIT = 25;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 100;

    private final DeviceSessionReadRepository readRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DeviceSessionPageResponse listSessions(
            UUID userAccountId, UUID currentSessionId, Integer limit, String cursorString) {
        Objects.requireNonNull(userAccountId, "userAccountId");
        Objects.requireNonNull(currentSessionId, "currentSessionId");

        var effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (effectiveLimit < MIN_LIMIT || effectiveLimit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }

        SessionCursor cursor = null;
        if (cursorString != null) {
            cursor = SessionCursorCodec.decode(cursorString);
        }

        var observedAt = clock.instant();
        var records = readRepository.findFamilies(userAccountId, currentSessionId, cursor, effectiveLimit + 1);

        var hasNext = records.size() > effectiveLimit;
        var pageRecords = hasNext ? records.subList(0, effectiveLimit) : records;

        var responses = pageRecords.stream()
                .map(record -> DeviceSessionResponse.from(record, observedAt))
                .toList();

        String nextCursor = null;
        if (hasNext && !responses.isEmpty()) {
            var lastResponse = responses.getLast();
            nextCursor = SessionCursorCodec.encode(lastResponse.createdAt(), lastResponse.familyId());
        }

        return new DeviceSessionPageResponse(responses, nextCursor);
    }

    @Transactional(readOnly = true)
    public DeviceSessionResponse getSessionDetail(UUID userAccountId, UUID currentSessionId, UUID familyId) {
        Objects.requireNonNull(userAccountId, "userAccountId");
        Objects.requireNonNull(currentSessionId, "currentSessionId");
        Objects.requireNonNull(familyId, "familyId");

        var observedAt = clock.instant();
        var record = readRepository
                .findFamilyDetail(userAccountId, currentSessionId, familyId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.SESSION_NOT_FOUND));

        return DeviceSessionResponse.from(record, observedAt);
    }
}
