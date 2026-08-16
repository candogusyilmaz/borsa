package dev.canverse.stocks.platform.error;

import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.reference.error.ReferenceErrorCode;
import java.util.Map;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;

/** Static lookup for the small set of migration-owned database constraints with public error contracts. */
public final class DatabaseConstraintRegistry {

    private static final Map<String, ErrorCode> MAPPINGS = Map.ofEntries(
            Map.entry("uq_user_account_email_normalized", IdentityErrorCode.EMAIL_ALREADY_REGISTERED),
            Map.entry("uq_auth_identity_provider_subject", IdentityErrorCode.EMAIL_ALREADY_REGISTERED),
            Map.entry("uix_reference_instrument_global_symbol", ReferenceErrorCode.DUPLICATE_INSTRUMENT),
            Map.entry("uix_reference_instrument_owner_symbol", ReferenceErrorCode.DUPLICATE_INSTRUMENT),
            Map.entry("uix_reference_instrument_alias_identity", ReferenceErrorCode.DUPLICATE_INSTRUMENT_ALIAS));

    private DatabaseConstraintRegistry() {}

    public static Optional<ErrorCode> resolve(Throwable exception) {
        var constraintName = constraintName(exception);
        return constraintName == null ? Optional.empty() : Optional.ofNullable(MAPPINGS.get(constraintName));
    }

    private static String constraintName(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return constraintViolation.getConstraintName();
            }
        }
        return null;
    }
}
