package dev.canverse.stocks.testing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Resets committed application fixtures while preserving global reference and migration data. */
public final class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate cleanupTransaction;

    DatabaseCleaner(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.cleanupTransaction = new TransactionTemplate(transactionManager);
        this.cleanupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void resetApplicationState() {
        cleanupTransaction.executeWithoutResult(status -> {
            // Security events have anonymous rows that do not cascade from an owner deletion.
            jdbcTemplate.update("DELETE FROM platform.security_event");

            // Deleting owners cascades through identity, account, ledger, portfolio, and import state.
            jdbcTemplate.update("DELETE FROM identity.user_account");

            // V2 seeds markets and currencies but no instruments; integration tests create these fixtures directly.
            jdbcTemplate.update("DELETE FROM reference.instrument");

            // V2 has no calendar seed rows; integration tests insert calendar fixtures directly.
            jdbcTemplate.update("DELETE FROM reference.market_calendar");
        });
    }
}
