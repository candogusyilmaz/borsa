package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.domain.DeviceSession;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionFamilyRecord;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionReadRepository;
import dev.canverse.stocks.identity.output.DeviceSessionPageResponse;
import dev.canverse.stocks.identity.output.DeviceSessionResponse;
import dev.canverse.stocks.identity.output.DeviceSessionStatus;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
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
        if (cursorString != null && !cursorString.isBlank()) {
            cursor = SessionCursorCodec.decode(cursorString);
        }

        var observedAt = clock.instant();
        var records = readRepository.findFamilies(userAccountId, currentSessionId, cursor, effectiveLimit + 1);

        var hasNext = records.size() > effectiveLimit;
        var pageRecords = hasNext ? records.subList(0, effectiveLimit) : records;

        var responses = new ArrayList<DeviceSessionResponse>(pageRecords.size());
        for (var record : pageRecords) {
            responses.add(toResponse(record, observedAt));
        }

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

        return toResponse(record, observedAt);
    }

    private DeviceSessionResponse toResponse(DeviceSessionFamilyRecord record, Instant observedAt) {
        if (!Objects.equals(record.minExpiresAt(), record.maxExpiresAt())) {
            throw new IllegalStateException("Inconsistent family expiry detected for family " + record.familyId());
        }

        var expiresAt = record.minExpiresAt();
        DeviceSessionStatus status;
        Instant endedAt;

        if (record.terminalRevokedAt() != null) {
            if (DeviceSession.REUSE_DETECTED_REVOKE_REASON.equals(record.terminalRevokeReason())) {
                status = DeviceSessionStatus.COMPROMISED;
            } else {
                status = DeviceSessionStatus.REVOKED;
            }
            endedAt = record.terminalRevokedAt();
        } else {
            if (expiresAt.isAfter(observedAt)) {
                status = DeviceSessionStatus.ACTIVE;
                endedAt = null;
            } else {
                status = DeviceSessionStatus.EXPIRED;
                endedAt = expiresAt;
            }
        }

        return new DeviceSessionResponse(
                record.familyId(),
                record.latestGenerationId(),
                record.deviceLabel(),
                record.createdAt(),
                record.lastUsedAt(),
                expiresAt,
                endedAt,
                status,
                record.current());
    }
}
