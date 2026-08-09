package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecureRefreshTokenGeneratorTest {

    @Test
    void generatedTokensAreIndependentHighEntropyValuesWithDeterministicHashes() throws NoSuchAlgorithmException {
        var generator = new SecureRefreshTokenGenerator();

        var first = generator.generate();
        var second = generator.generate();

        assertThat(Base64.getUrlDecoder().decode(first.rawToken())).hasSize(32);
        assertThat(Base64.getUrlDecoder().decode(second.rawToken())).hasSize(32);
        assertThat(first.rawToken()).doesNotContain("=");
        assertThat(first.hash()).doesNotContain("=");
        assertThat(first.hash()).isEqualTo(independentHash(first.rawToken()));
        assertThat(second.hash()).isEqualTo(independentHash(second.rawToken()));
        assertThat(first.rawToken()).isNotEqualTo(second.rawToken());
        assertThat(first.hash()).isNotEqualTo(second.hash());
        assertThat(first.rawToken()).isNotEqualTo(first.hash());
    }

    private String independentHash(String rawToken) throws NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256");
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }
}
