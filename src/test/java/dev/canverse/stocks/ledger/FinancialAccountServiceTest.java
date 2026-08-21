package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.ledger.application.FinancialAccountLifecycleService;
import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.application.FinancialAccountQueryService;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.request.OpeningCorrectionRequest;
import dev.canverse.stocks.ledger.web.request.OpeningStateRequest;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
class FinancialAccountServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    FinancialAccountOnboardingService accountService;

    @Autowired
    FinancialAccountQueryService queryService;

    @Autowired
    FinancialAccountLifecycleService lifecycleService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void fullLedgerOnboardingCreatesOpeningPostingPocketAndProjection() {
        var ownerId = insertUser("ledger-service-owner@example.com");
        var openingAt = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        var request = createRequest(
                UUID.randomUUID(),
                "Operating cash",
                AccountKind.CASH_CURRENT,
                TrackingMode.FULL_LEDGER,
                NegativeBalancePolicy.HARD_FLOOR,
                "100.00",
                openingAt);

        var response = accountService.create(ownerId, request);
        var balance = queryService.balance(ownerId, response.id(), null);

        assertThat(response.cashCoverageStatus().name()).isEqualTo("KNOWN_FROM_OPENING");
        assertThat(response.policyBreach()).isFalse();
        assertThat(response.version()).isEqualTo(1);
        assertThat(balance.ledgerBalance()).isEqualTo("100");
        assertThat(balance.policyBreach()).isFalse();
        assertThat(balance.clearedBalance()).isEqualTo("100");
        assertThat(balance.cashHeld()).isEqualTo("100");
        var historicalBalance = queryService.balance(ownerId, response.id(), openingAt);
        assertThat(historicalBalance.projectionStatus().name()).isEqualTo("NOT_APPLICABLE");
        assertThat(historicalBalance.watermarkRecordedAt()).isNull();
        assertThat(historicalBalance.watermarkActivityId()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger.money_posting WHERE owner_user_account_id = ?",
                        Integer.class,
                        ownerId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger.account_balance_projection WHERE owner_user_account_id = ?",
                        Integer.class,
                        ownerId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT p.last_applied_recorded_at = a.recorded_at"
                                + " FROM ledger.account_balance_projection p"
                                + " JOIN ledger.activity a ON a.id = p.last_applied_activity_id"
                                + " WHERE p.financial_account_id = ?",
                        Boolean.class,
                        response.id()))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT p.last_applied_recorded_at <> a.effective_at"
                                + " FROM ledger.account_balance_projection p"
                                + " JOIN ledger.activity a ON a.id = p.last_applied_activity_id"
                                + " WHERE p.financial_account_id = ?",
                        Boolean.class,
                        response.id()))
                .isTrue();
    }

    @Test
    void exactCreateRetryReturnsOriginalAndChangedReuseIsRejected() {
        var ownerId = insertUser("ledger-idempotency-owner@example.com");
        var request = createRequest(
                UUID.randomUUID(),
                "Savings",
                AccountKind.CASH_SAVINGS,
                TrackingMode.FULL_LEDGER,
                NegativeBalancePolicy.HARD_FLOOR,
                "0.00",
                Instant.now().minusSeconds(10));

        var first = accountService.create(ownerId, request);

        assertThat(jdbcTemplate.update("UPDATE reference.currency SET active = false WHERE code = 'USD'"))
                .isEqualTo(1);
        var replay = accountService.create(ownerId, request);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger.financial_account WHERE owner_user_account_id = ?",
                        Integer.class,
                        ownerId))
                .isEqualTo(1);

        var changed = new CreateFinancialAccountRequest(
                request.clientRequestId(),
                "Changed name",
                request.kind(),
                request.trackingMode(),
                request.currency(),
                request.timeZone(),
                request.policy(),
                request.authorizedLimit(),
                request.openingState());
        assertThatThrownBy(() -> accountService.create(ownerId, changed))
                .hasMessageContaining(LedgerErrorCode.IDEMPOTENCY_CONFLICT.getDescription());
    }

    @Test
    void holdingsOnlyBrokerageHasExplicitUntrackedCashAndNoLedgerRows() {
        var ownerId = insertUser("ledger-holdings-owner@example.com");
        var request = new CreateFinancialAccountRequest(
                UUID.randomUUID(),
                "Brokerage holdings",
                AccountKind.BROKERAGE,
                TrackingMode.HOLDINGS_ONLY,
                "USD",
                "UTC",
                null,
                null,
                null);

        var response = accountService.create(ownerId, request);
        var balance = queryService.balance(ownerId, response.id(), null);

        assertThat(response.cashCoverageStatus().name()).isEqualTo("UNTRACKED");
        assertThat(balance.coverageStatus().name()).isEqualTo("UNTRACKED");
        assertThat(balance.ledgerBalance()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger.account_cash_pocket WHERE financial_account_id = ?",
                        Integer.class,
                        response.id()))
                .isZero();
    }

    @Test
    void onboardingPreservesZeroNegativeAssetAndLiabilityOpeningSemantics() {
        var ownerId = insertUser("ledger-opening-boundaries@example.com");
        var zero = accountService.create(
                ownerId,
                createRequest(
                        UUID.randomUUID(),
                        "Zero cash",
                        AccountKind.CASH_CURRENT,
                        TrackingMode.FULL_LEDGER,
                        NegativeBalancePolicy.HARD_FLOOR,
                        "0.00",
                        Instant.now().minusSeconds(10)));
        var negative = accountService.create(
                ownerId,
                createRequest(
                        UUID.randomUUID(),
                        "Historical overdraft",
                        AccountKind.CASH_CURRENT,
                        TrackingMode.FULL_LEDGER,
                        NegativeBalancePolicy.TRACK_REALITY,
                        "-25.50",
                        Instant.now().minusSeconds(10)));
        var liability = accountService.create(
                ownerId,
                createRequest(
                        UUID.randomUUID(),
                        "Card balance",
                        AccountKind.CREDIT_CARD,
                        TrackingMode.FULL_LEDGER,
                        null,
                        "250",
                        Instant.now().minusSeconds(10)));

        var zeroBalance = queryService.balance(ownerId, zero.id(), null);
        assertThat(zeroBalance.ledgerBalance()).isEqualTo("0");
        assertThat(zeroBalance.cashHeld()).isEqualTo("0");
        assertThat(zeroBalance.overdraftUsed()).isEqualTo("0");

        var negativeBalance = queryService.balance(ownerId, negative.id(), null);
        assertThat(negativeBalance.ledgerBalance()).isEqualTo("-25.5");
        assertThat(negative.policyBreach()).isTrue();
        assertThat(negativeBalance.policyBreach()).isTrue();
        assertThat(negativeBalance.cashHeld()).isEqualTo("0");
        assertThat(negativeBalance.overdraftUsed()).isEqualTo("25.5");
        assertThat(negativeBalance.liabilityOutstanding()).isNull();

        var liabilityBalance = queryService.balance(ownerId, liability.id(), null);
        assertThat(liabilityBalance.ledgerBalance()).isEqualTo("250");
        assertThat(liabilityBalance.cashHeld()).isEqualTo("0");
        assertThat(liabilityBalance.liabilityOutstanding()).isEqualTo("250");
        assertThat(liabilityBalance.creditAvailable()).isNull();
        assertThat(liability.policyBreach()).isFalse();
    }

    @Test
    void exceptionalLiabilityAndAuthorizedLimitRealityExposeWarningsSafely() {
        var ownerId = insertUser("ledger-warning-boundaries@example.com");
        var effectiveAt = Instant.now().minusSeconds(10);
        var negativeLiability = accountService.create(
                ownerId,
                new CreateFinancialAccountRequest(
                        UUID.randomUUID(),
                        "Negative card reality",
                        AccountKind.CREDIT_CARD,
                        TrackingMode.FULL_LEDGER,
                        "USD",
                        "UTC",
                        null,
                        null,
                        new OpeningStateRequest("-75", effectiveAt)));
        var authorizedLimit = accountService.create(
                ownerId,
                new CreateFinancialAccountRequest(
                        UUID.randomUUID(),
                        "Over limit reality",
                        AccountKind.CASH_CURRENT,
                        TrackingMode.FULL_LEDGER,
                        "USD",
                        "UTC",
                        NegativeBalancePolicy.AUTHORIZED_LIMIT,
                        "50",
                        new OpeningStateRequest("-75", effectiveAt)));

        assertThat(negativeLiability.policyBreach()).isTrue();
        assertThat(queryService.balance(ownerId, negativeLiability.id(), null).policyBreach())
                .isTrue();

        var authorizedBalance = queryService.balance(ownerId, authorizedLimit.id(), null);
        assertThat(authorizedLimit.policyBreach()).isTrue();
        assertThat(authorizedBalance.policyBreach()).isTrue();
        assertThat(authorizedBalance.creditAvailable()).isEqualTo("0");
    }

    @Test
    void unknownCurrencyIsRejectedBeforeAnyLedgerRowsAreWritten() {
        var ownerId = insertUser("ledger-unknown-currency@example.com");

        assertThatThrownBy(() -> accountService.create(
                        ownerId,
                        createRequest(
                                UUID.randomUUID(),
                                "Unknown currency",
                                AccountKind.CASH_CURRENT,
                                TrackingMode.FULL_LEDGER,
                                NegativeBalancePolicy.HARD_FLOOR,
                                "10",
                                Instant.now().minusSeconds(10),
                                "ZZZ",
                                "UTC")))
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.ACCOUNT_CURRENCY_UNSUPPORTED);

        assertNoLedgerRows(ownerId);
    }

    @Test
    void inactiveCurrencyIsRejectedBeforeAnyLedgerRowsAreWritten() {
        var ownerId = insertUser("ledger-inactive-currency@example.com");
        assertThat(jdbcTemplate.update("UPDATE reference.currency SET active = false WHERE code = 'EUR'"))
                .isEqualTo(1);

        assertThatThrownBy(() -> accountService.create(
                        ownerId,
                        createRequest(
                                UUID.randomUUID(),
                                "Inactive currency",
                                AccountKind.CASH_CURRENT,
                                TrackingMode.FULL_LEDGER,
                                NegativeBalancePolicy.HARD_FLOOR,
                                "10",
                                Instant.now().minusSeconds(10),
                                "EUR",
                                "UTC")))
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.ACCOUNT_CURRENCY_UNSUPPORTED);

        assertNoLedgerRows(ownerId);
    }

    @Test
    void futureOpeningIsRejectedBeforeAnyLedgerRowsAreWritten() {
        var ownerId = insertUser("ledger-future-opening@example.com");

        assertThatThrownBy(() -> accountService.create(
                        ownerId,
                        createRequest(
                                UUID.randomUUID(),
                                "Future opening",
                                AccountKind.CASH_CURRENT,
                                TrackingMode.FULL_LEDGER,
                                NegativeBalancePolicy.HARD_FLOOR,
                                "10",
                                Instant.now().plusSeconds(60))))
                .isInstanceOf(AppException.class);

        assertNoLedgerRows(ownerId);
    }

    @Test
    void domainTimeZoneInvariantRejectsOffsetAndUnknownZones() {
        var ownerId = insertUser("ledger-time-zone@example.com");
        for (var timeZone : new String[] {"+02:00", "Not/AZone"}) {
            assertThatThrownBy(() -> accountService.create(
                            ownerId,
                            createRequest(
                                    UUID.randomUUID(),
                                    "Invalid " + timeZone,
                                    AccountKind.CASH_CURRENT,
                                    TrackingMode.FULL_LEDGER,
                                    NegativeBalancePolicy.HARD_FLOOR,
                                    "10",
                                    Instant.now().minusSeconds(10),
                                    "USD",
                                    timeZone)))
                    .isInstanceOf(AppException.class);
        }
        assertNoLedgerRows(ownerId);
    }

    @Test
    void openingCorrectionAppendsInverseAndReplacementFacts() {
        var ownerId = insertUser("ledger-correction-owner@example.com");
        var openingAt = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        var request = createRequest(
                UUID.randomUUID(),
                "Correction account",
                AccountKind.CASH_CURRENT,
                TrackingMode.FULL_LEDGER,
                NegativeBalancePolicy.TRACK_REALITY,
                "10",
                openingAt);
        var account = accountService.create(ownerId, request);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT current_opening_activity_id FROM ledger.financial_account WHERE id = ?",
                        UUID.class,
                        account.id()))
                .isNotNull();

        var correction = new OpeningCorrectionRequest(
                UUID.randomUUID(), "25.50", openingAt, "Bank statement correction", account.version());
        var corrected = lifecycleService.correctOpening(ownerId, account.id(), correction);
        var balance = queryService.balance(ownerId, account.id(), null);

        assertThat(corrected.version()).isEqualTo(2);
        assertThat(corrected.policyBreach()).isFalse();
        assertThat(balance.ledgerBalance()).isEqualTo("25.5");
        assertThat(balance.policyBreach()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger.money_posting WHERE owner_user_account_id = ?",
                        Integer.class,
                        ownerId))
                .isEqualTo(3);
    }

    @Test
    void openingCorrectionExposesAResultingNegativePolicyBreach() {
        var ownerId = insertUser("ledger-correction-breach-owner@example.com");
        var openingAt = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        var account = accountService.create(
                ownerId,
                createRequest(
                        UUID.randomUUID(),
                        "Correction breach",
                        AccountKind.CASH_CURRENT,
                        TrackingMode.FULL_LEDGER,
                        NegativeBalancePolicy.HARD_FLOOR,
                        "10",
                        openingAt));

        var corrected = lifecycleService.correctOpening(
                ownerId,
                account.id(),
                new OpeningCorrectionRequest(
                        UUID.randomUUID(), "-1", openingAt, "Historical negative correction", account.version()));

        assertThat(corrected.policyBreach()).isTrue();
        assertThat(queryService.balance(ownerId, account.id(), null).policyBreach())
                .isTrue();
    }

    private UUID insertUser(String email) {
        var id = UUID.randomUUID();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                id,
                email,
                email,
                now,
                now);
        return id;
    }

    private static CreateFinancialAccountRequest createRequest(
            UUID clientRequestId,
            String name,
            AccountKind kind,
            TrackingMode trackingMode,
            NegativeBalancePolicy policy,
            String amount,
            Instant effectiveAt) {
        return createRequest(clientRequestId, name, kind, trackingMode, policy, amount, effectiveAt, "USD", "UTC");
    }

    private static CreateFinancialAccountRequest createRequest(
            UUID clientRequestId,
            String name,
            AccountKind kind,
            TrackingMode trackingMode,
            NegativeBalancePolicy policy,
            String amount,
            Instant effectiveAt,
            String currency,
            String timeZone) {
        return new CreateFinancialAccountRequest(
                clientRequestId,
                name,
                kind,
                trackingMode,
                currency,
                timeZone,
                policy,
                null,
                new OpeningStateRequest(amount, effectiveAt));
    }

    private void assertNoLedgerRows(UUID ownerId) {
        for (var table : new String[] {
            "financial_account",
            "account_cash_pocket",
            "activity",
            "money_posting",
            "account_balance_projection",
            "idempotency_record"
        }) {
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM ledger." + table + " WHERE owner_user_account_id = ?",
                            Integer.class,
                            ownerId))
                    .as(table)
                    .isZero();
        }
    }
}
