package dev.canverse.stocks.platform.error;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creates the shared safe shape for dependency-free application validation failures. */
public final class ValidationErrors {

    private ValidationErrors() {}

    public static AppException invalidField(String field, String key, String detail) {
        return new AppException(
                CommonErrorCode.VALIDATION_FAILED,
                Map.of(
                        "errors",
                        List.of(Map.of(
                                "field", field,
                                "key", Objects.requireNonNull(key, "key"),
                                "detail", detail))));
    }
}
