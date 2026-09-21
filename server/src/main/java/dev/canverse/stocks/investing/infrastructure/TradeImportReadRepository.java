package dev.canverse.stocks.investing.infrastructure;

import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TradeImportReadRepository {

    private final JdbcClient jdbcClient;

    public List<ExistingTrade> findTradesAtEconomicOrder(UUID ownerUserAccountId, UUID accountId, Instant effectiveAt, long economicSequence) {
        return jdbcClient.sql("""
                SELECT a.id AS activity_id, s.instrument_id, a.activity_type, s.trade_currency_code,
                       ABS(s.quantity_delta) AS quantity, s.unit_price, s.gross_amount,
                       COALESCE(-fees.commission_amount, 0) AS commission_amount
                FROM ledger.activity a
                JOIN ledger.security_posting s
                  ON s.owner_user_account_id = a.owner_user_account_id AND s.activity_id = a.id
                LEFT JOIN (
                    SELECT owner_user_account_id, activity_id, SUM(amount) AS commission_amount
                    FROM ledger.money_posting
                    WHERE posting_role = 'FEE'
                    GROUP BY owner_user_account_id, activity_id
                ) fees ON fees.owner_user_account_id = a.owner_user_account_id AND fees.activity_id = a.id
                WHERE a.owner_user_account_id = :ownerUserAccountId
                  AND s.financial_account_id = :accountId
                  AND a.activity_type IN ('SECURITY_BUY', 'SECURITY_SELL')
                  AND a.effective_at = :effectiveAt
                  AND a.economic_sequence = :economicSequence
                  AND s.posting_role IN ('BUY', 'SELL')
                ORDER BY a.id
                """).param("ownerUserAccountId", ownerUserAccountId).param("accountId", accountId)
                .param("effectiveAt", OffsetDateTime.ofInstant(effectiveAt, java.time.ZoneOffset.UTC)).param("economicSequence", economicSequence)
                .query((resultSet, rowNumber) -> new ExistingTrade(resultSet.getObject("activity_id", UUID.class),
                        resultSet.getObject("instrument_id", UUID.class),
                        "SECURITY_BUY".equals(resultSet.getString("activity_type")) ? TradeSide.BUY : TradeSide.SELL,
                        resultSet.getString("trade_currency_code"), FinancialAmount.of(resultSet.getBigDecimal("quantity")),
                        FinancialAmount.of(resultSet.getBigDecimal("unit_price")), FinancialAmount.of(resultSet.getBigDecimal("gross_amount")),
                        FinancialAmount.of(resultSet.getBigDecimal("commission_amount"))))
                .list();
    }

    public Optional<UUID> findCommittedImportedActivity(UUID ownerUserAccountId, UUID accountId, String rowFingerprint) {
        return jdbcClient.sql("""
                SELECT a.id
                FROM ledger.trade_import_row r
                JOIN ledger.trade_import_batch b
                  ON b.owner_user_account_id = r.owner_user_account_id AND b.id = r.import_batch_id
                JOIN ledger.activity a
                  ON a.owner_user_account_id = r.owner_user_account_id AND a.source_import_row_id = r.id
                WHERE r.owner_user_account_id = :ownerUserAccountId
                  AND b.financial_account_id = :accountId
                  AND b.status = 'COMMITTED'
                  AND r.row_fingerprint = :rowFingerprint
                ORDER BY a.id
                LIMIT 1
                """).param("ownerUserAccountId", ownerUserAccountId).param("accountId", accountId).param("rowFingerprint", rowFingerprint).query(UUID.class)
                .optional();
    }

    public Map<UUID, CommittedActivity> findActivitiesByImportRow(UUID ownerUserAccountId, UUID batchId) {
        var links = jdbcClient.sql("""
                SELECT a.source_import_row_id, a.id, a.policy_decision
                FROM ledger.activity a
                JOIN ledger.trade_import_row r
                  ON r.owner_user_account_id = a.owner_user_account_id AND r.id = a.source_import_row_id
                WHERE a.owner_user_account_id = :ownerUserAccountId AND r.import_batch_id = :batchId
                ORDER BY r.source_record_number
                """).param("ownerUserAccountId", ownerUserAccountId).param("batchId", batchId)
                .query((resultSet, rowNumber) -> Map.entry(resultSet.getObject("source_import_row_id", UUID.class),
                        new CommittedActivity(resultSet.getObject("id", UUID.class), PolicyDecision.valueOf(resultSet.getString("policy_decision")))))
                .list();
        var result = new LinkedHashMap<UUID, CommittedActivity>();
        links.forEach(link -> result.put(link.getKey(), link.getValue()));
        return Map.copyOf(result);
    }

    public boolean lockVisibleInstrument(UUID ownerUserAccountId, UUID instrumentId) {
        return jdbcClient.sql("""
                SELECT id
                FROM reference.instrument
                WHERE id = :instrumentId
                  AND (owner_user_account_id IS NULL OR owner_user_account_id = :ownerUserAccountId)
                FOR UPDATE
                """).param("instrumentId", instrumentId).param("ownerUserAccountId", ownerUserAccountId).query(UUID.class).optional().isPresent();
    }

    public record ExistingTrade(
            UUID activityId,
            UUID instrumentId,
            TradeSide side,
            String currency,
            FinancialAmount quantity,
            FinancialAmount unitPrice,
            FinancialAmount grossAmount,
            FinancialAmount commissionAmount
    ) {}

    public record CommittedActivity(UUID activityId, PolicyDecision policyDecision) {}
}
