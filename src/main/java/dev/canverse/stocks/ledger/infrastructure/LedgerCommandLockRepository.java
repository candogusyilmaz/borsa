package dev.canverse.stocks.ledger.infrastructure;

import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Serializes retries for one owner-scoped ledger command until its transaction ends. */
@Repository
@RequiredArgsConstructor
public class LedgerCommandLockRepository {

    private final JdbcClient jdbcClient;

    public void lock(UUID ownerUserAccountId, String operationScope, UUID clientRequestId) {
        var lockKey = "%s:%s:%s"
                .formatted(
                        Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId"),
                        Objects.requireNonNull(operationScope, "operationScope"),
                        Objects.requireNonNull(clientRequestId, "clientRequestId"));
        jdbcClient
                .sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))")
                .param("lockKey", lockKey)
                .query((resultSet, rowNumber) -> 1)
                .single();
    }
}
