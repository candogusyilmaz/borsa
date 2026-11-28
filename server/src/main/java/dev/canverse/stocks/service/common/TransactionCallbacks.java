package dev.canverse.stocks.service.common;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Best-effort transaction callbacks for non-critical side effects such as operational logging. */
@Slf4j
public final class TransactionCallbacks {

    private TransactionCallbacks() {}

    /**
     * Runs {@code action} once the current actual transaction has committed, or immediately if no
     * actual transaction with synchronization is active.
     *
     * <p>When inside an actual transaction, the callback runs via {@link
     * TransactionSynchronization#afterCompletion(int)} only for {@link
     * TransactionSynchronization#STATUS_COMMITTED}. Runtime exceptions from {@code action} are
     * logged and suppressed so a committed business operation never appears to have failed because
     * of a logging callback.
     */
    public static void afterCommit(Runnable action) {
        Objects.requireNonNull(action, "action must not be null");

        if (TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        runSafely(action);
                    }
                }
            });
            return;
        }

        runSafely(action);
    }

    private static void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.atWarn()
                .addKeyValue("event", "transaction_callback_failed")
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .setCause(ex)
                .log("After-commit callback failed");
        }
    }
}
