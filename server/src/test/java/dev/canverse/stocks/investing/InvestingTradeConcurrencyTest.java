package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.investing.application.InvestingTradeCommandService;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.investing.web.request.TradeCommitRequest;
import dev.canverse.stocks.investing.web.request.TradePreviewRequest;
import dev.canverse.stocks.investing.web.response.TradePreviewResponse;
import dev.canverse.stocks.ledger.application.CashTransferService;
import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.request.OpeningStateRequest;
import dev.canverse.stocks.ledger.web.request.TransferRequest;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.ErrorCode;
import dev.canverse.stocks.reference.application.ManualInstrumentService;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.domain.ValuationMethod;
import dev.canverse.stocks.reference.web.request.ManualInstrumentCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class InvestingTradeConcurrencyTest {

    private static final UUID MANUAL_MARKET_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    FinancialAccountOnboardingService accountService;

    @Autowired
    CashTransferService transferService;

    @Autowired
    InvestingTradeCommandService tradeService;

    @Autowired
    ManualInstrumentService instrumentService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void concurrentFirstBuysWithDuplicateEconomicOrderCreateOnePosition() throws Exception {
        var fixture = fundedBrokerage("investing-first-position", "100");
        var at = fixture.tradeAt();
        var firstPreview = preview(fixture, "BUY", "1", "10", "0", at, 0);
        var secondPreview = preview(fixture, "BUY", "1", "10", "0", at, 0);
        var firstRequest = commitRequest(fixture, firstPreview, "BUY", "1", "10", "0", at, 0);
        var secondRequest = commitRequest(fixture, secondPreview, "BUY", "1", "10", "0", at, 0);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> commitAfter(start, fixture.ownerId(), firstRequest));
            var second = executor.submit(() -> commitAfter(start, fixture.ownerId(), secondRequest));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
            assertThat(outcomes.stream().map(Outcome::errorCode).filter(Objects::nonNull)).containsExactly(LedgerErrorCode.BALANCE_VERSION_CONFLICT);
            assertThat(positionQuantity(fixture)).isEqualTo("1");
            assertThat(count("SELECT COUNT(*) FROM ledger.position_projection WHERE owner_user_account_id = ?", fixture.ownerId())).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM ledger.security_posting WHERE owner_user_account_id = ? AND posting_role IN ('BUY', 'SELL')",
                    fixture.ownerId())).isEqualTo(1);
            assertThat(cashBalance(fixture.accountId())).isEqualByComparingTo("90");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentBuysCannotSpendTheSameBrokerageCashTwice() throws Exception {
        var fixture = fundedBrokerage("investing-concurrent-buys", "20");
        var at = fixture.tradeAt();
        var firstPreview = preview(fixture, "BUY", "1", "15", "0", at, 0);
        var secondPreview = preview(fixture, "BUY", "1", "15", "0", at, 1);
        var firstRequest = commitRequest(fixture, firstPreview, "BUY", "1", "15", "0", at, 0);
        var secondRequest = commitRequest(fixture, secondPreview, "BUY", "1", "15", "0", at, 1);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> commitAfter(start, fixture.ownerId(), firstRequest));
            var second = executor.submit(() -> commitAfter(start, fixture.ownerId(), secondRequest));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
            assertThat(outcomes.stream().map(Outcome::errorCode).filter(Objects::nonNull)).containsExactly(LedgerErrorCode.BALANCE_VERSION_CONFLICT);
            assertThat(cashBalance(fixture.accountId())).isEqualByComparingTo("5");
            assertThat(positionQuantity(fixture)).isEqualTo("1");
            assertThat(count("SELECT COUNT(*) FROM ledger.security_posting WHERE owner_user_account_id = ? AND posting_role IN ('BUY', 'SELL')",
                    fixture.ownerId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void simultaneousSellsDoNotOversellAndStaleCashAndPositionVersionsConflict() throws Exception {
        var fixture = fundedBrokerage("investing-concurrent-sells", "100");
        var buyAt = fixture.tradeAt();
        var buyPreview = preview(fixture, "BUY", "5", "10", "0", buyAt, 0);
        commitNow(fixture, commitRequest(fixture, buyPreview, "BUY", "5", "10", "0", buyAt, 0));

        var sellAt = buyAt.plusSeconds(1);
        var firstPreview = preview(fixture, "SELL", "4", "12", "0", sellAt, 1);
        var secondPreview = preview(fixture, "SELL", "4", "12", "0", sellAt, 2);
        var firstRequest = commitRequest(fixture, firstPreview, "SELL", "4", "12", "0", sellAt, 1);
        var secondRequest = commitRequest(fixture, secondPreview, "SELL", "4", "12", "0", sellAt, 2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> commitAfter(start, fixture.ownerId(), firstRequest));
            var second = executor.submit(() -> commitAfter(start, fixture.ownerId(), secondRequest));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
            assertThat(outcomes.stream().map(Outcome::errorCode).filter(Objects::nonNull)).containsExactly(LedgerErrorCode.BALANCE_VERSION_CONFLICT);
        } finally {
            executor.shutdownNow();
        }

        assertThat(positionQuantity(fixture)).isEqualTo("1");
        assertThat(cashBalance(fixture.accountId())).isEqualByComparingTo("98");
        var currentPreview = preview(fixture, "SELL", "1", "12", "0", buyAt.plusSeconds(2), 3);
        var stalePosition = withExpectedPositionVersion(commitRequest(fixture, currentPreview, "SELL", "1", "12", "0", buyAt.plusSeconds(2), 3), 0);
        assertThatThrownBy(() -> commitNow(fixture, stalePosition))
                .satisfies(exception -> assertThat(errorCode(exception)).isEqualTo(InvestingErrorCode.POSITION_VERSION_CONFLICT));

        var staleCash = withExpectedCashVersion(commitRequest(fixture, currentPreview, "SELL", "1", "12", "0", buyAt.plusSeconds(2), 3),
                firstPreview.cashBalanceVersion());
        assertThatThrownBy(() -> commitNow(fixture, staleCash))
                .satisfies(exception -> assertThat(errorCode(exception)).isEqualTo(LedgerErrorCode.BALANCE_VERSION_CONFLICT));
        assertThat(positionQuantity(fixture)).isEqualTo("1");
    }

    @Test
    void duplicateEconomicOrderAndOversellRollbackAllTradeFacts() throws Exception {
        var fixture = fundedBrokerage("investing-invalid-history", "100");
        var at = fixture.tradeAt();
        var buyPreview = preview(fixture, "BUY", "4", "10", "0", at, 0);
        commitNow(fixture, commitRequest(fixture, buyPreview, "BUY", "4", "10", "0", at, 0));
        var activityCount = count("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?", fixture.ownerId());
        var moneyPostingCount = count("SELECT COUNT(*) FROM ledger.money_posting WHERE owner_user_account_id = ?", fixture.ownerId());
        var securityPostingCount = count("SELECT COUNT(*) FROM ledger.security_posting WHERE owner_user_account_id = ?", fixture.ownerId());
        var idempotencyCount = count("SELECT COUNT(*) FROM ledger.idempotency_record WHERE owner_user_account_id = ?", fixture.ownerId());

        var duplicatePreview = preview(fixture, "BUY", "1", "10", "0", at.plusSeconds(1), 1);
        var candidateRequest = commitRequest(fixture, duplicatePreview, "BUY", "1", "10", "0", at.plusSeconds(1), 1);
        var duplicateRequest = withEconomicOrder(candidateRequest, at, 0);
        assertThatThrownBy(() -> commitNow(fixture, duplicateRequest))
                .satisfies(exception -> assertThat(errorCode(exception)).isEqualTo(InvestingErrorCode.DUPLICATE_ECONOMIC_ORDER));

        var sellPreview = preview(fixture, "SELL", "1", "10", "0", at.plusSeconds(1), 1);
        var oversell = commitRequest(fixture, sellPreview, "SELL", "5", "10", "0", at.plusSeconds(1), 1);
        assertThatThrownBy(() -> commitNow(fixture, oversell))
                .satisfies(exception -> assertThat(errorCode(exception)).isEqualTo(InvestingErrorCode.INSUFFICIENT_POSITION_QUANTITY));

        assertThat(count("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?", fixture.ownerId())).isEqualTo(activityCount);
        assertThat(count("SELECT COUNT(*) FROM ledger.money_posting WHERE owner_user_account_id = ?", fixture.ownerId())).isEqualTo(moneyPostingCount);
        assertThat(count("SELECT COUNT(*) FROM ledger.security_posting WHERE owner_user_account_id = ?", fixture.ownerId())).isEqualTo(securityPostingCount);
        assertThat(count("SELECT COUNT(*) FROM ledger.idempotency_record WHERE owner_user_account_id = ?", fixture.ownerId())).isEqualTo(idempotencyCount);
        assertThat(positionQuantity(fixture)).isEqualTo("4");
        assertThat(cashBalance(fixture.accountId())).isEqualByComparingTo("60");
    }

    private Fixture fundedBrokerage(String label, String brokerageOpening) {
        var ownerId = insertUser(label);
        var now = Instant.now();
        var source = createAccount(ownerId, label + " source", "1000", now.minusSeconds(30), AccountKind.CASH_CURRENT);
        var brokerage = createAccount(ownerId, label + " brokerage", "0", now.minusSeconds(30), AccountKind.BROKERAGE);
        transactionManagerTemplate().executeWithoutResult(status -> transferService.transfer(ownerId, new TransferRequest(UUID.randomUUID(), source, brokerage,
                brokerageOpening, RecordingMode.CURRENT_ACTION, now.minusSeconds(20), false, null, null)));
        var instrumentId = transactionManagerTemplate().execute(status -> instrumentService.create(ownerId,
                new ManualInstrumentCreateRequest(MANUAL_MARKET_ID, "EQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "Concurrency equity",
                        InstrumentType.EQUITY, "USD", ValuationMethod.NOT_VALUED, List.of())))
                .id();
        return new Fixture(ownerId, brokerage, instrumentId, now.minusSeconds(10));
    }

    private UUID createAccount(UUID ownerId, String name, String openingAmount, Instant effectiveAt, AccountKind kind) {
        var response = transactionManagerTemplate().execute(status -> accountService.create(ownerId, new CreateFinancialAccountRequest(UUID.randomUUID(), name,
                kind, TrackingMode.FULL_LEDGER, "USD", "UTC", NegativeBalancePolicy.HARD_FLOOR, null, new OpeningStateRequest(openingAmount, effectiveAt))));
        return Objects.requireNonNull(response).id();
    }

    private TradePreviewResponse preview(Fixture fixture, String side, String quantity, String unitPrice, String commission, Instant at, long sequence) {
        return tradeService.preview(fixture.ownerId(), new TradePreviewRequest(fixture.accountId(), fixture.instrumentId(), TradeSide.valueOf(side), quantity,
                unitPrice, commission, RecordingMode.CURRENT_ACTION, at, sequence, false));
    }

    private TradeCommitRequest commitRequest(Fixture fixture, TradePreviewResponse preview, String side, String quantity, String unitPrice, String commission,
            Instant at, long sequence) {
        return new TradeCommitRequest(UUID.randomUUID(), fixture.accountId(), fixture.instrumentId(), TradeSide.valueOf(side), quantity, unitPrice, commission,
                RecordingMode.CURRENT_ACTION, at, sequence, false, preview.cashBalanceVersion(), preview.positionVersion());
    }

    private TradeCommitRequest withExpectedCashVersion(TradeCommitRequest request, long version) {
        return new TradeCommitRequest(request.clientRequestId(), request.accountId(), request.instrumentId(), request.side(), request.quantity(),
                request.unitPrice(), request.commissionAmount(), request.recordingMode(), request.effectiveAt(), request.economicSequence(),
                request.confirmPolicyBreach(), version, request.expectedPositionVersion());
    }

    private TradeCommitRequest withExpectedPositionVersion(TradeCommitRequest request, long version) {
        return new TradeCommitRequest(request.clientRequestId(), request.accountId(), request.instrumentId(), request.side(), request.quantity(),
                request.unitPrice(), request.commissionAmount(), request.recordingMode(), request.effectiveAt(), request.economicSequence(),
                request.confirmPolicyBreach(), request.expectedCashBalanceVersion(), version);
    }

    private TradeCommitRequest withEconomicOrder(TradeCommitRequest request, Instant at, long sequence) {
        return new TradeCommitRequest(request.clientRequestId(), request.accountId(), request.instrumentId(), request.side(), request.quantity(),
                request.unitPrice(), request.commissionAmount(), request.recordingMode(), at, sequence, request.confirmPolicyBreach(),
                request.expectedCashBalanceVersion(), request.expectedPositionVersion());
    }

    private void commitNow(Fixture fixture, TradeCommitRequest request) {
        transactionManagerTemplate().executeWithoutResult(status -> tradeService.commit(fixture.ownerId(), request));
    }

    private Outcome commitAfter(CountDownLatch start, UUID ownerId, TradeCommitRequest request) {
        await(start);
        try {
            var response = transactionManagerTemplate().execute(status -> tradeService.commit(ownerId, request));
            return Outcome.success(Objects.requireNonNull(response).id());
        } catch (Throwable exception) {
            return Outcome.failure(errorCode(exception));
        }
    }

    private String positionQuantity(Fixture fixture) {
        return jdbcTemplate
                .queryForObject(
                        "SELECT current_quantity FROM ledger.position_projection WHERE owner_user_account_id = ?" +
                                " AND financial_account_id = ? AND instrument_id = ?",
                        BigDecimal.class, fixture.ownerId(), fixture.accountId(), fixture.instrumentId())
                .stripTrailingZeros().toPlainString();
    }

    private BigDecimal cashBalance(UUID accountId) {
        return jdbcTemplate.queryForObject("SELECT ledger_balance FROM ledger.account_balance_projection WHERE financial_account_id = ?", BigDecimal.class,
                accountId);
    }

    private Integer count(String sql, UUID ownerId) {
        return jdbcTemplate.queryForObject(sql, Integer.class, ownerId);
    }

    private UUID insertUser(String label) {
        var id = UUID.randomUUID();
        var email = label + "+" + id + "@concurrency.test";
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        transactionManagerTemplate().executeWithoutResult(status -> jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", id, email, email, now, now));
        return id;
    }

    private TransactionTemplate transactionManagerTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private static ErrorCode errorCode(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof AppException appException) {
                return appException.getErrorCode();
            }
        }
        return null;
    }

    private static void await(CountDownLatch start) {
        try {
            if (!start.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrency test did not start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency test interrupted", exception);
        }
    }

    private record Fixture(UUID ownerId, UUID accountId, UUID instrumentId, Instant tradeAt) {}

    private record Outcome(UUID tradeId, ErrorCode errorCode) {
        static Outcome success(UUID tradeId) {
            return new Outcome(tradeId, null);
        }

        static Outcome failure(ErrorCode errorCode) {
            return new Outcome(null, errorCode);
        }

        boolean succeeded() {
            return tradeId != null;
        }
    }
}
