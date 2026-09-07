package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.configuration.AccessTokenProperties;
import dev.canverse.stocks.identity.configuration.LocalAccessTokenConfiguration;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class AccessTokenPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(LocalAccessTokenConfiguration.class);

    @Test
    void absentPropertiesBindExactDefaultsAndCreateOneLocalRsaSigner() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            var properties = context.getBean(AccessTokenProperties.class);
            assertThat(properties.issuer()).isEqualTo(URI.create("https://canverse.dev"));
            assertThat(properties.audience()).isEqualTo("canverse-api");
            assertThat(properties.lifetime()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.keyId()).isEqualTo("local-ephemeral");

            assertThat(context.getBeansOfType(KeyPair.class)).hasSize(1);
            assertThat(context.getBeansOfType(JwtEncoder.class)).hasSize(1);
            assertThat(context.getBeansOfType(JwtDecoder.class)).hasSize(1);
            var keyPair = context.getBean(KeyPair.class);
            assertThat(context.getBean(KeyPair.class)).isSameAs(keyPair);
            assertThat(context.getBean(JwtEncoder.class)).isSameAs(context.getBean(JwtEncoder.class));
            assertThat(keyPair.getPublic()).isInstanceOf(RSAPublicKey.class);
            assertThat(((RSAPublicKey) keyPair.getPublic()).getModulus().bitLength()).isGreaterThanOrEqualTo(2048);

            var issuedAt = Instant.parse("2026-08-09T10:00:00Z");
            var headers = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).type("access").build();
            var claims = JwtClaimsSet.builder().subject("configuration-test").issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(60)).build();
            var encoded = context.getBean(JwtEncoder.class).encode(JwtEncoderParameters.from(headers, claims));
            var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).signatureAlgorithm(SignatureAlgorithm.RS256).validateType(false)
                    .build();
            decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());

            var decoded = decoder.decode(encoded.getTokenValue());

            assertThat(decoded.getHeaders()).containsOnlyKeys("alg", "kid", "typ").containsEntry("alg", "RS256").containsEntry("kid", "local-ephemeral")
                    .containsEntry("typ", "access");
        });
    }

    @Test
    void invalidPropertyValuesAreRejected() {
        assertThatThrownBy(() -> new AccessTokenProperties(null, "audience", Duration.ofMinutes(1), "key", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("issuer must be an absolute URI");
        assertThatThrownBy(() -> new AccessTokenProperties(URI.create("https://issuer.test"), null, Duration.ofMinutes(1), "key", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("audience must not be blank");
        assertThatThrownBy(() -> new AccessTokenProperties(URI.create("https://issuer.test"), "audience", null, "key", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("lifetime must be positive");
        assertThatThrownBy(() -> new AccessTokenProperties(URI.create("https://issuer.test"), "audience", Duration.ofMinutes(1), null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("keyId must not be blank");

        var invalidValues = List.of(new InvalidProperty("stocks.identity.access-token.issuer=relative/path", "issuer must be an absolute URI"),
                new InvalidProperty("stocks.identity.access-token.audience= ", "audience must not be blank"),
                new InvalidProperty("stocks.identity.access-token.lifetime=0s", "lifetime must be positive"),
                new InvalidProperty("stocks.identity.access-token.lifetime=-1s", "lifetime must be positive"),
                new InvalidProperty("stocks.identity.access-token.key-id= ", "keyId must not be blank"));

        for (var invalidValue : invalidValues) {
            contextRunner.withPropertyValues(invalidValue.property()).run(context -> assertThat(rootCause(context.getStartupFailure()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage(invalidValue.expectedMessage()));
        }
    }

    @Test
    void configuredPkcs8AndX509PemKeysAreLoadedAsOneSigningPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var configuredKeyPair = generator.generateKeyPair();

        contextRunner.withPropertyValues("stocks.identity.access-token.private-key-pem=" + pem("PRIVATE KEY", configuredKeyPair.getPrivate().getEncoded()),
                "stocks.identity.access-token.public-key-pem=" + pem("PUBLIC KEY", configuredKeyPair.getPublic().getEncoded())).run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    var loadedKeyPair = context.getBean(KeyPair.class);
                    assertThat(loadedKeyPair.getPrivate().getEncoded()).containsExactly(configuredKeyPair.getPrivate().getEncoded());
                    assertThat(loadedKeyPair.getPublic().getEncoded()).containsExactly(configuredKeyPair.getPublic().getEncoded());
                });
    }

    @Test
    void keyMaterialMustBeConfiguredAsACompletePair() {
        assertThatThrownBy(() -> new AccessTokenProperties(URI.create("https://issuer.test"), "audience", Duration.ofMinutes(1), "key", "private", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("privateKeyPem and publicKeyPem must be configured together");
    }

    private Throwable rootCause(Throwable throwable) {
        assertThat(throwable).isNotNull();
        var rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    private record InvalidProperty(String property, String expectedMessage) {}

    private String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----" + Base64.getEncoder().encodeToString(encoded) + "-----END " + type + "-----";
    }
}
