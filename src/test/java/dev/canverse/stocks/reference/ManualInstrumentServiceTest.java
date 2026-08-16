package dev.canverse.stocks.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.ErrorCode;
import dev.canverse.stocks.platform.id.IdGenerator;
import dev.canverse.stocks.reference.application.ManualInstrumentService;
import dev.canverse.stocks.reference.domain.AliasType;
import dev.canverse.stocks.reference.domain.ManualInstrumentConstraints;
import dev.canverse.stocks.reference.domain.ValuationMethod;
import dev.canverse.stocks.reference.error.ReferenceErrorCode;
import dev.canverse.stocks.reference.input.InstrumentAliasInput;
import dev.canverse.stocks.reference.input.ManualInstrumentCreateRequest;
import dev.canverse.stocks.reference.input.ManualInstrumentUpdateRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Import(ManualInstrumentServiceTest.TestOverrides.class)
class ManualInstrumentServiceTest {

    private static final UUID MANUAL_MARKET = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final Instant T0 = Instant.parse("2026-08-16T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    ManualInstrumentService instrumentService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    FailingIdGenerator idGenerator;

    @Autowired
    CoordinatingClock clock;

    @BeforeEach
    void cleanDatabase() {
        idGenerator.fail.set(false);
        clock.stopCoordinating();
        clock.setInstant(T0);
        jdbcTemplate.execute("TRUNCATE TABLE reference.instrument_alias, reference.instrument, platform.security_event,"
                + " identity.device_session, identity.auth_identity, identity.user_account CASCADE");
        jdbcTemplate.update("UPDATE reference.market SET active = true");
        jdbcTemplate.update("UPDATE reference.currency SET active = true");
    }

    @Test
    void createsOwnerInstrumentWithNormalizedIdentityAndAliases() {
        var ownerId = register("manual-create@example.com");
        var response = instrumentService.create(
                ownerId,
                createRequest(
                        " my-fund ",
                        " My manually valued fund ",
                        List.of(new InstrumentAliasInput(AliasType.USER, " Pension Fund "))));

        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.marketId()).isEqualTo(MANUAL_MARKET);
        assertThat(response.symbol()).isEqualTo("my-fund");
        assertThat(response.name()).isEqualTo("My manually valued fund");
        assertThat(response.quotationCurrency()).isEqualTo("GBP");
        assertThat(response.valuationMethod()).isEqualTo(ValuationMethod.MANUAL_VALUE);
        assertThat(response.active()).isTrue();
        assertThat(response.sourceKind()).isEqualTo("USER_ENTERED");
        assertThat(response.version()).isZero();
        assertThat(response.aliases()).extracting(alias -> alias.value()).containsExactly("Pension Fund");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT symbol_normalized FROM reference.instrument WHERE id = ?", String.class, response.id()))
                .isEqualTo("MY-FUND");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT name_normalized FROM reference.instrument WHERE id = ?", String.class, response.id()))
                .isEqualTo("MY MANUALLY VALUED FUND");
    }

    @Test
    void aliasesAreBoundedSortedAndImmutableAtTheResponseBoundary() {
        var ownerId = register("manual-alias-contract@example.com");
        var maximumAliases = IntStream.range(0, ManualInstrumentConstraints.MAX_ALIASES_PER_INSTRUMENT)
                .mapToObj(index -> new InstrumentAliasInput(AliasType.USER, "alias-%02d".formatted(index)))
                .toList();

        var maximum =
                instrumentService.create(ownerId, createRequest("MAX-ALIASES", "Maximum aliases", maximumAliases));
        assertThat(maximum.aliases()).hasSize(ManualInstrumentConstraints.MAX_ALIASES_PER_INSTRUMENT);

        var ordered = instrumentService.create(
                ownerId,
                createRequest(
                        "ORDERED-ALIASES",
                        "Ordered aliases",
                        List.of(
                                new InstrumentAliasInput(AliasType.USER, "zeta"),
                                new InstrumentAliasInput(AliasType.TICKER, "beta"),
                                new InstrumentAliasInput(AliasType.USER, "alpha"))));
        assertThat(ordered.aliases())
                .extracting(alias -> alias.type() + ":" + alias.value())
                .containsExactly("TICKER:beta", "USER:alpha", "USER:zeta");
        assertThatThrownBy(() -> ordered.aliases().add(ordered.aliases().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);

        var tooManyAliases = IntStream.range(0, ManualInstrumentConstraints.MAX_ALIASES_PER_INSTRUMENT + 1)
                .mapToObj(index -> new InstrumentAliasInput(AliasType.USER, "too-many-%02d".formatted(index)))
                .toList();
        assertThatThrownBy(() -> instrumentService.create(
                        ownerId, createRequest("TOO-MANY-ALIASES", "Too many aliases", tooManyAliases)))
                .isInstanceOfAny(
                        jakarta.validation.ConstraintViolationException.class,
                        org.springframework.validation.method.MethodValidationException.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument WHERE owner_user_account_id = ? AND symbol = ?",
                        Integer.class,
                        ownerId,
                        "TOO-MANY-ALIASES"))
                .isZero();
    }

    @Test
    void updateIsOwnerOnlyVersionedAndCanReactivateAnInactiveInstrument() {
        var ownerId = register("manual-update@example.com");
        var otherOwnerId = register("manual-other@example.com");
        var created = instrumentService.create(ownerId, createRequest("UPDATE-ME", "Before", List.of()));

        var updated = instrumentService.update(
                ownerId,
                created.id(),
                new ManualInstrumentUpdateRequest(
                        created.version(),
                        " After ",
                        ValuationMethod.NOT_VALUED,
                        false,
                        List.of(new InstrumentAliasInput(AliasType.TICKER, "after"))));

        assertThat(updated.name()).isEqualTo("After");
        assertThat(updated.valuationMethod()).isEqualTo(ValuationMethod.NOT_VALUED);
        assertThat(updated.active()).isFalse();
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.aliases()).extracting(alias -> alias.value()).containsExactly("after");
        assertThat(instrumentService.get(ownerId, created.id()).active()).isFalse();
        assertThatThrownBy(() -> instrumentService.get(otherOwnerId, created.id()))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ReferenceErrorCode.INSTRUMENT_NOT_FOUND));
        assertThatThrownBy(() -> instrumentService.update(
                        otherOwnerId,
                        created.id(),
                        new ManualInstrumentUpdateRequest(1, "leak", ValuationMethod.NOT_VALUED, true, List.of())))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ReferenceErrorCode.INSTRUMENT_NOT_FOUND));
    }

    @Test
    void aliasOnlyReplacementForcesParentVersionEvenWhenMetadataAndClockAreUnchanged() {
        var ownerId = register("manual-alias-only@example.com");
        var created = instrumentService.create(
                ownerId,
                createRequest(
                        "ALIAS-ONLY",
                        "Unchanged metadata",
                        List.of(new InstrumentAliasInput(AliasType.USER, "before"))));

        var updated = instrumentService.update(
                ownerId,
                created.id(),
                new ManualInstrumentUpdateRequest(
                        0,
                        "Unchanged metadata",
                        ValuationMethod.MANUAL_VALUE,
                        true,
                        List.of(new InstrumentAliasInput(AliasType.USER, "after"))));

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.name()).isEqualTo(created.name());
        assertThat(updated.updatedAt()).isEqualTo(created.updatedAt());
        assertThat(updated.aliases()).extracting(alias -> alias.value()).containsExactly("after");
    }

    @Test
    void aliasOnlyReplacementUsesOrdinaryVersionCheckWhenClockMoves() {
        var ownerId = register("manual-alias-clock-moved@example.com");
        var created = instrumentService.create(
                ownerId,
                createRequest(
                        "ALIAS-CLOCK",
                        "Unchanged metadata",
                        List.of(new InstrumentAliasInput(AliasType.USER, "before"))));
        var nextInstant = T0.plusSeconds(1);
        clock.setInstant(nextInstant);

        var updated = instrumentService.update(
                ownerId,
                created.id(),
                new ManualInstrumentUpdateRequest(
                        0,
                        "Unchanged metadata",
                        ValuationMethod.MANUAL_VALUE,
                        true,
                        List.of(new InstrumentAliasInput(AliasType.USER, "after"))));

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.updatedAt()).isEqualTo(nextInstant);
        assertThat(updated.aliases()).extracting(alias -> alias.value()).containsExactly("after");
    }

    @Test
    void normalizationExpansionIsRejectedBeforeAnyInstrumentWrite() {
        var ownerId = register("manual-normalization@example.com");
        var expandingName = "ß".repeat(81);
        var expandingAlias = "ß".repeat(65);

        assertThatThrownBy(() -> instrumentService.create(
                        ownerId,
                        createRequest(
                                "EXPANDING", expandingName, List.of(new InstrumentAliasInput(AliasType.USER, "safe")))))
                .isInstanceOf(AppException.class)
                .satisfies(exception ->
                        assertThat(((AppException) exception).getCode()).isEqualTo("VALIDATION_FAILED"));
        assertThatThrownBy(() -> instrumentService.create(
                        ownerId,
                        createRequest(
                                "EXPANDING-ALIAS",
                                "Safe name",
                                List.of(new InstrumentAliasInput(AliasType.USER, expandingAlias)))))
                .isInstanceOf(AppException.class)
                .satisfies(exception ->
                        assertThat(((AppException) exception).getCode()).isEqualTo("VALIDATION_FAILED"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument WHERE owner_user_account_id = ?",
                        Integer.class,
                        ownerId))
                .isZero();

        var created = instrumentService.create(ownerId, createRequest("EXPANSION-UPDATE", "Stable name", List.of()));
        assertThatThrownBy(() -> instrumentService.update(
                        ownerId,
                        created.id(),
                        new ManualInstrumentUpdateRequest(
                                0, expandingName, ValuationMethod.MANUAL_VALUE, true, List.of())))
                .isInstanceOf(AppException.class)
                .satisfies(exception ->
                        assertThat(((AppException) exception).getCode()).isEqualTo("VALIDATION_FAILED"));
        assertThatThrownBy(() -> instrumentService.update(
                        ownerId,
                        created.id(),
                        new ManualInstrumentUpdateRequest(
                                0,
                                "Stable name",
                                ValuationMethod.MANUAL_VALUE,
                                true,
                                List.of(new InstrumentAliasInput(AliasType.USER, expandingAlias)))))
                .isInstanceOf(AppException.class)
                .satisfies(exception ->
                        assertThat(((AppException) exception).getCode()).isEqualTo("VALIDATION_FAILED"));
        assertThat(instrumentService.get(ownerId, created.id()).version()).isZero();
    }

    @Test
    void paddedMaximumDisplayValuesAreTrimmedBeforeBothCreateAndUpdateBounds() {
        var ownerId = register("manual-padded-boundary@example.com");
        var symbol = " " + "S".repeat(ManualInstrumentConstraints.MAX_SYMBOL_LENGTH) + " ";
        var name = " " + "N".repeat(ManualInstrumentConstraints.MAX_NAME_LENGTH) + " ";
        var alias = " " + "A".repeat(ManualInstrumentConstraints.MAX_ALIAS_VALUE_LENGTH) + " ";

        var created = instrumentService.create(
                ownerId,
                new ManualInstrumentCreateRequest(
                        MANUAL_MARKET,
                        symbol,
                        name,
                        dev.canverse.stocks.reference.domain.InstrumentType.FUND,
                        "GBP",
                        ValuationMethod.MANUAL_VALUE,
                        List.of(new InstrumentAliasInput(AliasType.USER, alias))));

        assertThat(created.symbol()).hasSize(ManualInstrumentConstraints.MAX_SYMBOL_LENGTH);
        assertThat(created.name()).hasSize(ManualInstrumentConstraints.MAX_NAME_LENGTH);
        assertThat(created.aliases().getFirst().value()).hasSize(ManualInstrumentConstraints.MAX_ALIAS_VALUE_LENGTH);

        var updated = instrumentService.update(
                ownerId,
                created.id(),
                new ManualInstrumentUpdateRequest(
                        0,
                        name,
                        ValuationMethod.NOT_VALUED,
                        true,
                        List.of(new InstrumentAliasInput(AliasType.USER, alias))));
        assertThat(updated.name()).hasSize(ManualInstrumentConstraints.MAX_NAME_LENGTH);
        assertThat(updated.aliases().getFirst().value()).hasSize(ManualInstrumentConstraints.MAX_ALIAS_VALUE_LENGTH);
    }

    @Test
    void duplicateAndUnsupportedWritesReturnStableErrorsWithoutPartialRows() {
        var ownerId = register("manual-errors@example.com");
        instrumentService.create(ownerId, createRequest("SAME", "Same one", List.of()));

        assertThatThrownBy(() -> instrumentService.create(ownerId, createRequest("same", "Same two", List.of())))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ReferenceErrorCode.DUPLICATE_INSTRUMENT));
        assertThatThrownBy(() -> instrumentService.create(
                        ownerId,
                        new ManualInstrumentCreateRequest(
                                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                                "UNSUPPORTED",
                                "Unsupported",
                                dev.canverse.stocks.reference.domain.InstrumentType.FUND,
                                "USD",
                                ValuationMethod.MANUAL_VALUE,
                                List.of())))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ReferenceErrorCode.UNSUPPORTED_MARKET_CURRENCY));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument WHERE owner_user_account_id = ?",
                        Integer.class,
                        ownerId))
                .isEqualTo(1);
    }

    @Test
    void unknownAndInactiveReferencesRejectBeforeAnyWrite() {
        var ownerId = register("manual-reference-errors@example.com");

        assertReferenceError(
                () -> instrumentService.create(
                        ownerId,
                        new ManualInstrumentCreateRequest(
                                UUID.randomUUID(),
                                "UNKNOWN-MARKET",
                                "Unknown market",
                                dev.canverse.stocks.reference.domain.InstrumentType.FUND,
                                "GBP",
                                ValuationMethod.MANUAL_VALUE,
                                List.of())),
                ReferenceErrorCode.MARKET_NOT_FOUND);
        assertReferenceError(
                () -> instrumentService.create(
                        ownerId,
                        new ManualInstrumentCreateRequest(
                                MANUAL_MARKET,
                                "UNKNOWN-CURRENCY",
                                "Unknown currency",
                                dev.canverse.stocks.reference.domain.InstrumentType.FUND,
                                "ZZZ",
                                ValuationMethod.MANUAL_VALUE,
                                List.of())),
                ReferenceErrorCode.CURRENCY_NOT_FOUND);

        updateReference("UPDATE reference.market SET active = false WHERE id = ?", MANUAL_MARKET);
        assertReferenceError(
                () -> instrumentService.create(ownerId, createRequest("INACTIVE-MARKET", "Inactive market", List.of())),
                ReferenceErrorCode.INACTIVE_REFERENCE);
        updateReference("UPDATE reference.market SET active = true WHERE id = ?", MANUAL_MARKET);

        updateReference("UPDATE reference.currency SET active = false WHERE code = 'GBP'");
        assertReferenceError(
                () -> instrumentService.create(
                        ownerId, createRequest("INACTIVE-CURRENCY", "Inactive currency", List.of())),
                ReferenceErrorCode.INACTIVE_REFERENCE);
        updateReference("UPDATE reference.currency SET active = true WHERE code = 'GBP'");

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument WHERE owner_user_account_id = ?",
                        Integer.class,
                        ownerId))
                .isZero();
    }

    @Test
    void aliasReplacementFailureRollsBackPreviousVersionAndAliasSet() {
        var ownerId = register("manual-atomic@example.com");
        var created = instrumentService.create(
                ownerId,
                createRequest("ATOMIC", "Original", List.of(new InstrumentAliasInput(AliasType.USER, "original"))));
        idGenerator.fail.set(true);

        assertThatThrownBy(() -> instrumentService.update(
                        ownerId,
                        created.id(),
                        new ManualInstrumentUpdateRequest(
                                0,
                                "Changed",
                                ValuationMethod.NOT_VALUED,
                                false,
                                List.of(new InstrumentAliasInput(AliasType.USER, "changed")))))
                .isInstanceOf(IllegalStateException.class);

        idGenerator.fail.set(false);
        var unchanged = instrumentService.get(ownerId, created.id());
        assertThat(unchanged.name()).isEqualTo("Original");
        assertThat(unchanged.version()).isZero();
        assertThat(unchanged.active()).isTrue();
        assertThat(unchanged.aliases()).extracting(alias -> alias.value()).containsExactly("original");
    }

    @Test
    void staleVersionIsRejectedWithoutChangingMetadata() {
        var ownerId = register("manual-version@example.com");
        var created = instrumentService.create(ownerId, createRequest("VERSION", "Version one", List.of()));
        instrumentService.update(
                ownerId,
                created.id(),
                new ManualInstrumentUpdateRequest(0, "Version two", ValuationMethod.NOT_VALUED, true, List.of()));

        assertThatThrownBy(() -> instrumentService.update(
                        ownerId,
                        created.id(),
                        new ManualInstrumentUpdateRequest(
                                0, "Lost update", ValuationMethod.MANUAL_VALUE, false, List.of())))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ReferenceErrorCode.INSTRUMENT_VERSION_CONFLICT));
        assertThat(instrumentService.get(ownerId, created.id()).name()).isEqualTo("Version two");
    }

    @Test
    void concurrentUpdatesAllowOnlyOneVersionedCommit() throws Exception {
        var ownerId = register("manual-concurrent@example.com");
        var created = instrumentService.create(ownerId, createRequest("CONCURRENT", "Before", List.of()));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentUpdate(ownerId, created.id(), "First", ready, start));
            var second = executor.submit(() -> concurrentUpdate(ownerId, created.id(), "Second", ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(outcomes)
                    .extracting(UpdateOutcome::errorCode)
                    .containsExactlyInAnyOrder(null, ReferenceErrorCode.INSTRUMENT_VERSION_CONFLICT);
            var successful =
                    outcomes.stream().filter(UpdateOutcome::success).findFirst().orElseThrow();
            var finalInstrument = instrumentService.get(ownerId, created.id());
            assertThat(finalInstrument.version()).isEqualTo(1);
            assertThat(finalInstrument.name()).isEqualTo(successful.name());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentAliasOnlyUpdatesThatLoadedVersionZeroHaveOneOptimisticWinner() throws Exception {
        var ownerId = register("manual-alias-concurrent@example.com");
        var created = instrumentService.create(
                ownerId,
                createRequest(
                        "ALIAS-CONCURRENT",
                        "Stable metadata",
                        List.of(new InstrumentAliasInput(AliasType.USER, "initial"))));
        clock.coordinateNextTwoCalls();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentAliasOnlyUpdate(ownerId, created.id(), "first"));
            var second = executor.submit(() -> concurrentAliasOnlyUpdate(ownerId, created.id(), "second"));

            assertThat(clock.awaitTwoCalls(10, TimeUnit.SECONDS)).isTrue();
            clock.release();
            var outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(outcomes)
                    .extracting(UpdateOutcome::errorCode)
                    .containsExactlyInAnyOrder(null, ReferenceErrorCode.INSTRUMENT_VERSION_CONFLICT);
            assertThat(instrumentService.get(ownerId, created.id()).version()).isEqualTo(1);
        } finally {
            clock.release();
            clock.stopCoordinating();
            executor.shutdownNow();
        }
    }

    private UpdateOutcome concurrentUpdate(
            UUID ownerId, UUID instrumentId, String name, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent update test did not start");
            }
            var response = instrumentService.update(
                    ownerId,
                    instrumentId,
                    new ManualInstrumentUpdateRequest(0, name, ValuationMethod.MANUAL_VALUE, true, List.of()));
            return new UpdateOutcome(response.name(), null);
        } catch (AppException exception) {
            return new UpdateOutcome(null, exception.getErrorCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent update test interrupted", exception);
        }
    }

    private UpdateOutcome concurrentAliasOnlyUpdate(UUID ownerId, UUID instrumentId, String alias) {
        try {
            var response = instrumentService.update(
                    ownerId,
                    instrumentId,
                    new ManualInstrumentUpdateRequest(
                            0,
                            "Stable metadata",
                            ValuationMethod.MANUAL_VALUE,
                            true,
                            List.of(new InstrumentAliasInput(AliasType.USER, alias))));
            return new UpdateOutcome(response.aliases().getFirst().value(), null);
        } catch (AppException exception) {
            return new UpdateOutcome(null, exception.getErrorCode());
        }
    }

    private UUID register(String email) {
        return registrationService.register(email, "correct horse battery staple");
    }

    private static void assertReferenceError(Runnable action, ReferenceErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AppException.class)
                .satisfies(exception ->
                        assertThat(((AppException) exception).getErrorCode()).isEqualTo(expected));
    }

    private void updateReference(String sql, Object... parameters) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbcTemplate.update(sql, parameters));
    }

    private static ManualInstrumentCreateRequest createRequest(
            String symbol, String name, List<InstrumentAliasInput> aliases) {
        return new ManualInstrumentCreateRequest(
                MANUAL_MARKET,
                symbol,
                name,
                dev.canverse.stocks.reference.domain.InstrumentType.FUND,
                "GBP",
                ValuationMethod.MANUAL_VALUE,
                aliases);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {

        @Bean
        @Primary
        CoordinatingClock fixedClock() {
            return new CoordinatingClock(T0);
        }

        @Bean
        @Primary
        FailingIdGenerator idGenerator() {
            return new FailingIdGenerator();
        }
    }

    static final class CoordinatingClock extends Clock {

        private final Clock delegate;
        private volatile Instant currentInstant;
        private volatile Coordination coordination;

        CoordinatingClock(Instant instant) {
            delegate = Clock.fixed(instant, ZoneOffset.UTC);
            currentInstant = instant;
        }

        void setInstant(Instant instant) {
            currentInstant = instant;
        }

        void coordinateNextTwoCalls() {
            coordination = new Coordination(new CountDownLatch(2), new CountDownLatch(1));
        }

        boolean awaitTwoCalls(long timeout, TimeUnit unit) throws InterruptedException {
            return coordination.arrived().await(timeout, unit);
        }

        void release() {
            var current = coordination;
            if (current != null) {
                current.release().countDown();
            }
        }

        void stopCoordinating() {
            coordination = null;
        }

        @Override
        public ZoneId getZone() {
            return delegate.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return delegate.withZone(zone);
        }

        @Override
        public Instant instant() {
            var current = coordination;
            if (current != null) {
                current.arrived().countDown();
                try {
                    current.release().await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Coordinating clock interrupted", exception);
                }
            }
            return currentInstant;
        }

        private record Coordination(CountDownLatch arrived, CountDownLatch release) {}
    }

    static final class FailingIdGenerator implements IdGenerator {

        private final AtomicBoolean fail = new AtomicBoolean();

        @Override
        public UUID next() {
            if (fail.get()) {
                throw new IllegalStateException("forced id failure");
            }
            return UUID.randomUUID();
        }
    }

    private record UpdateOutcome(String name, ErrorCode errorCode) {

        boolean success() {
            return errorCode == null;
        }
    }
}
