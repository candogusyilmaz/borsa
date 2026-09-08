package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.domain.IdempotencyRecord;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.IdempotencyRecordRepository;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.id.IdGenerator;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Stores and replays the serialized result of a ledger command. */
@Component
@RequiredArgsConstructor
final class LedgerIdempotencyStore {

    private final IdempotencyRecordRepository repository;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    <T> T replay(UUID clientRequestId, UUID ownerId, String scope, String hash, Class<T> responseType) {
        var record = repository.findByKey(ownerId, scope, clientRequestId).orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.getRequestHash().equals(hash)) {
            throw new AppException(LedgerErrorCode.IDEMPOTENCY_CONFLICT);
        }
        try {
            return objectMapper.readValue(record.getResultSnapshot(), responseType);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored ledger idempotency snapshot is unreadable", exception);
        }
    }

    void save(UUID ownerId, String scope, UUID clientRequestId, String hash, String resourceKind, UUID resourceId, Object response, Instant createdAt) {
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(response);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to store ledger idempotency result", exception);
        }
        repository.save(IdempotencyRecord.create(idGenerator.next(), ownerId, scope, clientRequestId, hash, resourceKind, resourceId, snapshot, createdAt));
    }
}
