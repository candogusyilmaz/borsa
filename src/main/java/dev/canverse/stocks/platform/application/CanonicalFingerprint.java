package dev.canverse.stocks.platform.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Produces stable SHA-256 fingerprints for normalized semantic values. */
@Component
@RequiredArgsConstructor
public class CanonicalFingerprint {

    private final ObjectMapper objectMapper;

    public String hash(Map<String, ?> semanticValue) {
        try {
            return hashBytes(objectMapper.writeValueAsBytes(new LinkedHashMap<>(semanticValue)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    public String hashText(String semanticValue) {
        try {
            return hashBytes(semanticValue.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    public Map<String, Object> values(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must contain pairs");
        }
        var values = new LinkedHashMap<String, Object>();
        for (var index = 0; index < keyValues.length; index += 2) {
            values.put((String) keyValues[index], keyValues[index + 1]);
        }
        return values;
    }

    private static String hashBytes(byte[] bytes) throws NoSuchAlgorithmException {
        return java.util.HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
