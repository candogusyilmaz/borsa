package dev.canverse.stocks.platform.error;

import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Describes a stable application error without coupling it to an HTTP response body.
 */
public interface ErrorCode {

    HttpStatus getStatus();

    String getDescription();

    Set<String> getRequiredParams();

    default String getCode() {
        if (this instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        throw new IllegalStateException("ErrorCode implementations must be enums");
    }

    default String getMessageKey() {
        var simpleName = getClass().getSimpleName();
        var domainName = simpleName.endsWith("ErrorCode") ? simpleName.substring(0, simpleName.length() - "ErrorCode".length()) : simpleName;
        return "error.%s.%s".formatted(toSnakeCase(domainName), getCode().toLowerCase(Locale.ROOT));
    }

    default HttpStatus status() {
        return getStatus();
    }

    default String description() {
        return getDescription();
    }

    default Set<String> requiredParamKeys() {
        return getRequiredParams();
    }

    default String code() {
        return getCode();
    }

    default String messageKey() {
        return getMessageKey();
    }

    default Set<String> getParamKeys() {
        return getRequiredParams();
    }

    private static String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
