package dev.canverse.stocks.identity.infrastructure;

import dev.canverse.stocks.identity.application.GeneratedRefreshToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class SecureRefreshTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedRefreshToken generate() {
        var randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);

        var rawToken = BASE64_ENCODER.encodeToString(randomBytes);
        return new GeneratedRefreshToken(rawToken, hash(rawToken));
    }

    public String hash(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken");
        return BASE64_ENCODER.encodeToString(newMessageDigest().digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }

    private MessageDigest newMessageDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
