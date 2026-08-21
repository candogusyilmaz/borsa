package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.domain.AccountBalanceProjection;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.IdempotencyRecord;
import dev.canverse.stocks.ledger.domain.MoneyPosting;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.infrastructure.AccountBalanceProjectionRepository;
import dev.canverse.stocks.ledger.infrastructure.IdempotencyRecordRepository;
import dev.canverse.stocks.ledger.infrastructure.MoneyPostingRepository;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.request.OpeningStateRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class LedgerTransactionRollbackTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    FinancialAccountOnboardingService accountService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    MoneyPostingRepository moneyPostingRepository;

    @MockitoSpyBean
    AccountBalanceProjectionRepository projectionRepository;

    @MockitoSpyBean
    IdempotencyRecordRepository idempotencyRecordRepository;

    @BeforeEach
    void cleanDatabase() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbcTemplate.execute(
                        "TRUNCATE TABLE ledger.money_posting, ledger.activity, ledger.account_balance_projection,"
                                + " ledger.account_cash_pocket, ledger.idempotency_record, ledger.financial_account,"
                                + " identity.user_account CASCADE"));
        reset(moneyPostingRepository, projectionRepository, idempotencyRecordRepository);
    }

    @Test
    void postingFailureRollsBackTheEntireOnboardingWorkflow() {
        var ownerId = insertUser("posting-failure");
        doThrow(new DataIntegrityViolationException("posting failure"))
                .when(moneyPostingRepository)
                .save(any(MoneyPosting.class));

        assertThatThrownBy(() -> accountService.create(ownerId, request()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertNoLedgerRows(ownerId);
    }

    @Test
    void projectionFailureRollsBackFactsAndAccountState() {
        var ownerId = insertUser("projection-failure");
        doThrow(new DataIntegrityViolationException("projection failure"))
                .when(projectionRepository)
                .save(any(AccountBalanceProjection.class));

        assertThatThrownBy(() -> accountService.create(ownerId, request()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertNoLedgerRows(ownerId);
    }

    @Test
    void idempotencyFailureRollsBackFactsAndAccountState() {
        var ownerId = insertUser("idempotency-failure");
        doThrow(new DataIntegrityViolationException("idempotency failure"))
                .when(idempotencyRecordRepository)
                .save(any(IdempotencyRecord.class));

        assertThatThrownBy(() -> accountService.create(ownerId, request()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertNoLedgerRows(ownerId);
    }

    private CreateFinancialAccountRequest request() {
        return new CreateFinancialAccountRequest(
                UUID.randomUUID(),
                "Rollback account",
                AccountKind.CASH_CURRENT,
                TrackingMode.FULL_LEDGER,
                "USD",
                "UTC",
                NegativeBalancePolicy.HARD_FLOOR,
                null,
                new OpeningStateRequest("25", Instant.now().minusSeconds(10)));
    }

    private UUID insertUser(String suffix) {
        var id = UUID.randomUUID();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var email = id + "+" + suffix + "@rollback.test";
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbcTemplate.update(
                        "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at)"
                                + " VALUES (?, ?, ?, ?, ?)",
                        id,
                        email,
                        email,
                        now,
                        now));
        return id;
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
