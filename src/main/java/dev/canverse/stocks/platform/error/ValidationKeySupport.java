package dev.canverse.stocks.platform.error;

import java.util.Map;
import java.util.regex.Pattern;

final class ValidationKeySupport {

    static final String FALLBACK_KEY = "error.fields.common.unmapped_constraint";

    private static final Pattern APPLICATION_KEY = Pattern.compile(
            "^error\\.(?:fields\\.)?[a-z][a-z0-9]*(?:_[a-z0-9]+)*(?:\\.[a-z][a-z0-9]*(?:_[a-z0-9]+)*)+$");

    private static final Map<String, String> BUILT_IN_KEYS = Map.ofEntries(
            Map.entry("NotNull", "error.fields.common.not_null"),
            Map.entry("NotBlank", "error.fields.common.not_blank"),
            Map.entry("NotEmpty", "error.fields.common.not_empty"),
            Map.entry("Size", "error.fields.common.size"),
            Map.entry("Min", "error.fields.common.min"),
            Map.entry("Max", "error.fields.common.max"),
            Map.entry("DecimalMin", "error.fields.common.decimal_min"),
            Map.entry("DecimalMax", "error.fields.common.decimal_max"),
            Map.entry("Positive", "error.fields.common.positive"),
            Map.entry("PositiveOrZero", "error.fields.common.positive_or_zero"),
            Map.entry("Negative", "error.fields.common.negative"),
            Map.entry("NegativeOrZero", "error.fields.common.negative_or_zero"),
            Map.entry("Email", "error.fields.common.email"),
            Map.entry("Pattern", "error.fields.common.pattern"),
            Map.entry("Past", "error.fields.common.past"),
            Map.entry("Future", "error.fields.common.future"),
            Map.entry("Digits", "error.fields.common.digits"));

    private ValidationKeySupport() {}

    static boolean isValidApplicationKey(String key) {
        return key != null && APPLICATION_KEY.matcher(key).matches();
    }

    static String explicitApplicationKey(String messageTemplate) {
        if (messageTemplate == null || messageTemplate.isBlank()) {
            return null;
        }
        var candidate = messageTemplate;
        if (candidate.startsWith("{") && candidate.endsWith("}")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        } else if (candidate.contains("{") || candidate.contains("}")) {
            return null;
        }
        return isValidApplicationKey(candidate) ? candidate : null;
    }

    static String builtInKey(String constraintName) {
        return BUILT_IN_KEYS.get(constraintName);
    }

    static String safeDetail(String constraintName, String messageTemplate, boolean explicitKey) {
        if (explicitKey) {
            return "Validation failed.";
        }
        return switch (constraintName == null ? "" : constraintName) {
            case "NotNull" -> "must not be null";
            case "NotBlank" -> "must not be blank";
            case "NotEmpty" -> "must not be empty";
            case "Size" -> "has an invalid size";
            case "Min" -> "must be greater than or equal to the minimum";
            case "Max" -> "must be less than or equal to the maximum";
            case "DecimalMin" -> "must be greater than or equal to the minimum";
            case "DecimalMax" -> "must be less than or equal to the maximum";
            case "Positive" -> "must be positive";
            case "PositiveOrZero" -> "must be greater than or equal to zero";
            case "Negative" -> "must be negative";
            case "NegativeOrZero" -> "must be less than or equal to zero";
            case "Email" -> "must be a well-formed email address";
            case "Pattern" -> "has an invalid format";
            case "Past" -> "must be in the past";
            case "Future" -> "must be in the future";
            case "Digits" -> "has an invalid number of digits";
            default -> "Validation failed.";
        };
    }
}
