package dev.canverse.stocks.platform.error;

import java.util.Map;
import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/** Translates only explicitly known database constraints into safe application errors. */
public final class DatabaseConstraintTranslator {

    private DatabaseConstraintTranslator() {}

    public static RuntimeException translate(
            DataIntegrityViolationException exception, Map<String, ? extends ErrorCode> knownConstraints) {
        Objects.requireNonNull(exception, "exception");
        Objects.requireNonNull(knownConstraints, "knownConstraints");

        var constraintName = constraintName(exception);
        if (constraintName == null) {
            return exception;
        }
        var errorCode = knownConstraints.get(constraintName);
        return errorCode == null ? exception : new AppException(errorCode, exception);
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
