package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.investing.application.PortfolioService;
import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.investing.web.request.ArchivePortfolioRequest;
import dev.canverse.stocks.investing.web.request.CreatePortfolioRequest;
import dev.canverse.stocks.investing.web.request.UpdatePortfolioRequest;
import dev.canverse.stocks.investing.web.response.PortfolioResponse;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.ErrorCode;
import dev.canverse.stocks.testing.DatabaseCleaner;
import dev.canverse.stocks.testing.IntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@IntegrationTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Execution(ExecutionMode.SAME_THREAD)
class PortfolioConcurrencyTest {

    @Autowired
    DatabaseCleaner databaseCleaner;

    @BeforeEach
    void resetDatabase() {
        databaseCleaner.resetApplicationState();
    }
    @Autowired
    PortfolioService portfolioService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void concurrentReplacementAndArchiveHaveOneWinnerAndOneVersionConflict() throws Exception {
        var ownerId = insertUser();
        var firstAccountId = insertAccount(ownerId, "First account");
        var secondAccountId = insertAccount(ownerId, "Second account");

        var updateRace = portfolioService.create(ownerId, new CreatePortfolioRequest("Update race", List.of(firstAccountId, secondAccountId)));
        var updateOutcomes = race(
                () -> portfolioService.update(ownerId, updateRace.id(), new UpdatePortfolioRequest("First winner", List.of(firstAccountId), 0L)),
                () -> portfolioService.update(ownerId, updateRace.id(), new UpdatePortfolioRequest("Second winner", List.of(secondAccountId), 0L)));

        assertOneVersionConflict(updateOutcomes);
        var updateResult = portfolioService.get(ownerId, updateRace.id());
        assertThat(updateResult.version()).isEqualTo(1L);
        if (updateResult.name().equals("First winner")) {
            assertThat(updateResult.accounts()).extracting(account -> account.id()).containsExactly(firstAccountId);
        } else {
            assertThat(updateResult.name()).isEqualTo("Second winner");
            assertThat(updateResult.accounts()).extracting(account -> account.id()).containsExactly(secondAccountId);
        }

        var archiveRace = portfolioService.create(ownerId, new CreatePortfolioRequest("Archive race", List.of(firstAccountId)));
        var archiveOutcomes = race(
                () -> portfolioService.update(ownerId, archiveRace.id(), new UpdatePortfolioRequest("Updated", List.of(secondAccountId), 0L)),
                () -> portfolioService.archive(ownerId, archiveRace.id(), new ArchivePortfolioRequest(0L)));

        assertOneVersionConflict(archiveOutcomes);
        var archiveResult = portfolioService.get(ownerId, archiveRace.id());
        assertThat(archiveResult.version()).isEqualTo(1L);
        if (archiveResult.archived()) {
            assertThat(archiveResult.accounts()).extracting(account -> account.id()).containsExactly(firstAccountId);
        } else {
            assertThat(archiveResult.name()).isEqualTo("Updated");
            assertThat(archiveResult.accounts()).extracting(account -> account.id()).containsExactly(secondAccountId);
        }
    }

    private List<Outcome> race(PortfolioCommand firstCommand, PortfolioCommand secondCommand) throws Exception {
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runAfter(start, firstCommand));
            var second = executor.submit(() -> runAfter(start, secondCommand));
            start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertOneVersionConflict(List<Outcome> outcomes) {
        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
        assertThat(outcomes.stream().map(Outcome::errorCode).filter(code -> code != null)).containsExactly(InvestingErrorCode.PORTFOLIO_VERSION_CONFLICT);
    }

    private static Outcome runAfter(CountDownLatch start, PortfolioCommand command) {
        try {
            if (!start.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("portfolio concurrency test did not start");
            }
            return Outcome.success(command.run());
        } catch (Throwable exception) {
            return Outcome.failure(errorCode(exception));
        }
    }

    private UUID insertUser() {
        var id = UUID.randomUUID();
        var email = id + "@portfolio-concurrency.test";
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", id, email, email, now, now));
        return id;
    }

    private UUID insertAccount(UUID ownerId, String name) {
        var id = UUID.randomUUID();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbcTemplate.update(
                "INSERT INTO ledger.financial_account" +
                        " (id, owner_user_account_id, name, name_normalized, account_kind, tracking_mode, negative_balance_policy, currency_code, time_zone," +
                        " created_at, updated_at, version) VALUES (?, ?, ?, ?, 'BROKERAGE', 'HOLDINGS_ONLY', NULL, 'USD', 'UTC', ?, ?, 0)",
                id, ownerId, name, name.toUpperCase(), now, now));
        return id;
    }

    private static ErrorCode errorCode(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof AppException appException) {
                return appException.getErrorCode();
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface PortfolioCommand {
        PortfolioResponse run();
    }

    private record Outcome(PortfolioResponse response, ErrorCode errorCode) {
        static Outcome success(PortfolioResponse response) {
            return new Outcome(response, null);
        }

        static Outcome failure(ErrorCode errorCode) {
            return new Outcome(null, errorCode);
        }

        boolean succeeded() {
            return response != null;
        }
    }
}
