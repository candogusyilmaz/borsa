package dev.canverse.stocks.service.common;

import static dev.canverse.stocks.service.common.TransactionCallbacks.afterCommit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class TransactionCallbacksTests {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(TransactionCallbacks.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        clearTransactionState();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        clearTransactionState();
    }

    @Test
    void runsCallbackOnlyAfterActualTransactionCommits() {
        var txManager = new StubTransactionManager();
        var txTemplate = new TransactionTemplate(txManager);
        var counter = new AtomicInteger();

        txTemplate.executeWithoutResult(
                status -> {
                    afterCommit(counter::incrementAndGet);
                    assertThat(counter.get()).isZero();
                });

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void doesNotRunCallbackWhenTransactionRollsBack() {
        var txManager = new StubTransactionManager();
        var txTemplate = new TransactionTemplate(txManager);
        var counter = new AtomicInteger();

        assertThatThrownBy(
                        () ->
                                txTemplate.executeWithoutResult(
                                        status -> {
                                            afterCommit(counter::incrementAndGet);
                                            throw new IllegalStateException("business failure");
                                        }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(counter.get()).isZero();
    }

    @Test
    void doesNotRunCallbackWhenTransactionOutcomeIsUnknown() {
        var counter = new AtomicInteger();

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        afterCommit(counter::incrementAndGet);
        assertThat(counter.get()).isZero();

        for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        }

        assertThat(counter.get()).isZero();
    }

    @Test
    void runsImmediatelyOutsideAnyTransaction() {
        var counter = new AtomicInteger();

        afterCommit(counter::incrementAndGet);

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void runsImmediatelyWhenSynchronizationIsActiveWithoutActualTransaction() {
        var counter = new AtomicInteger();

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);

        afterCommit(counter::incrementAndGet);

        assertThat(counter.get()).isEqualTo(1);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void suppressesCallbackFailureAfterCommitSoCommittedTransactionDoesNotFail() {
        var txManager = new StubTransactionManager();
        var txTemplate = new TransactionTemplate(txManager);
        var secondCallbackRan = new AtomicInteger();

        assertThatCode(
                        () ->
                                txTemplate.executeWithoutResult(
                                        status -> {
                                            afterCommit(
                                                    () -> {
                                                        throw new IllegalStateException(
                                                                "logging failure");
                                                    });
                                            afterCommit(secondCallbackRan::incrementAndGet);
                                        }))
                .doesNotThrowAnyException();

        assertThat(txManager.committed).isTrue();
        assertThat(secondCallbackRan.get()).isEqualTo(1);
        assertThat(appender.list).hasSize(1);
        var event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(toMap(event))
                .containsEntry("event", "transaction_callback_failed")
                .containsEntry("exception", "IllegalStateException");
    }

    @Test
    void suppressesCallbackFailureOutsideTransaction() {
        assertThatCode(
                        () ->
                                afterCommit(
                                        () -> {
                                            throw new RuntimeException("formatter bug");
                                        }))
                .doesNotThrowAnyException();

        assertThat(appender.list).hasSize(1);
        var event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(toMap(event))
                .containsEntry("event", "transaction_callback_failed")
                .containsEntry("exception", "RuntimeException");
    }

    @Test
    void rejectsNullAction() {
        assertThatThrownBy(() -> afterCommit(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action must not be null");
    }

    private static void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private static Map<String, Object> toMap(ILoggingEvent event) {
        if (event.getKeyValuePairs() == null) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, Object>();
        for (var kv : event.getKeyValuePairs()) {
            map.put(kv.key, kv.value);
        }
        return map;
    }

    private static final class StubTransactionManager extends AbstractPlatformTransactionManager {
        private boolean committed;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            this.committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
