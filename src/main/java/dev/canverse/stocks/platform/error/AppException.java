package dev.canverse.stocks.platform.error;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Base exception for expected application failures with a strict parameter contract.
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> params;

    public AppException(ErrorCode errorCode) {
        this(errorCode, null, Map.of());
    }

    public AppException(ErrorCode errorCode, Map<String, ?> params) {
        this(errorCode, null, params);
    }

    public AppException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, cause, Map.of());
    }

    public AppException(ErrorCode errorCode, Throwable cause, Map<String, ?> params) {
        super(Objects.requireNonNull(errorCode, "errorCode").getDescription(), cause);
        this.errorCode = errorCode;
        this.params = immutableParams(params);
        validateParamKeys(errorCode, this.params.keySet());
    }

    public AppException(ErrorCode errorCode, Map<String, ?> params, Throwable cause) {
        this(errorCode, cause, params);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public String getMessageKey() {
        return errorCode.getMessageKey();
    }

    public String getCode() {
        return errorCode.getCode();
    }

    private static void validateParamKeys(ErrorCode errorCode, Set<String> suppliedKeys) {
        if (!suppliedKeys.equals(errorCode.getRequiredParams())) {
            throw new IllegalStateException("Error parameters do not match the required contract for " + errorCode.getCode() + ": required=" +
                    errorCode.getRequiredParams() + ", supplied=" + suppliedKeys);
        }
    }

    private static Map<String, Object> immutableParams(Map<String, ?> source) {
        var values = source == null ? Map.<String, Object>of() : source;
        var copy = new LinkedHashMap<String, Object>();
        for (var entry : values.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "param key"), immutableValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<Object, Object>();
            for (var entry : map.entrySet()) {
                copy.put(immutableValue(entry.getKey()), immutableValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Collection<?> collection) {
            var copy = new ArrayList<Object>(collection.size());
            for (var item : collection) {
                copy.add(immutableValue(item));
            }
            return List.copyOf(copy);
        }
        if (value != null && value.getClass().isArray()) {
            var copy = new ArrayList<Object>(Array.getLength(value));
            for (var index = 0; index < Array.getLength(value); index++) {
                copy.add(immutableValue(Array.get(value, index)));
            }
            return List.copyOf(copy);
        }
        return value;
    }
}
