package dev.canverse.stocks.identity.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class LocalAccessTokenDecoderTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-09T14:00:00Z");
    private static final Instant FRACTIONAL_OBSERVED_AT = Instant.parse("2026-08-09T14:00:00.750Z");
    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(5);
    private static final String ISSUER = "https://issuer.test";
    private static final String AUDIENCE = "canverse-test-api";
    private static final String KEY_ID = "test-ephemeral";
    private static final String SUBJECT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String TOKEN_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String SESSION_ID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";

    private final RecordingClock clock = new RecordingClock(FRACTIONAL_OBSERVED_AT);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(Clock.class, () -> clock)
            .withUserConfiguration(LocalAccessTokenConfiguration.class)
            .withPropertyValues(
                    "stocks.identity.access-token.issuer=" + ISSUER,
                    "stocks.identity.access-token.audience=" + AUDIENCE,
                    "stocks.identity.access-token.lifetime=5m",
                    "stocks.identity.access-token.key-id=" + KEY_ID);

    @Test
    void validIssuedEnvelopeDecodesThroughTheSingleLocalKeyConfiguration() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBeansOfType(KeyPair.class)).hasSize(1);
            assertThat(context.getBeansOfType(JwtEncoder.class)).hasSize(1);
            assertThat(context.getBeansOfType(JwtDecoder.class)).hasSize(1);
            var keyPair = context.getBean(KeyPair.class);
            var encoder = context.getBean(JwtEncoder.class);
            var decoder = context.getBean(JwtDecoder.class);
            var expiresAt = ISSUED_AT.plus(ACCESS_TOKEN_LIFETIME);
            var compactToken = encode(encoder, ISSUED_AT, expiresAt);
            clock.reset(FRACTIONAL_OBSERVED_AT);

            var decoded = decoder.decode(compactToken);

            assertThat(clock.invocations()).isOne();
            assertThat(decoded.getHeaders())
                    .containsOnlyKeys("alg", "kid", "typ")
                    .containsEntry("alg", "RS256")
                    .containsEntry("kid", KEY_ID)
                    .containsEntry("typ", "access");
            assertThat(decoded.getClaims()).containsOnlyKeys("iss", "sub", "aud", "iat", "nbf", "exp", "jti", "sid");
            assertThat(decoded.getIssuer().toString()).isEqualTo(ISSUER);
            assertThat(decoded.getSubject()).isEqualTo(SUBJECT);
            assertThat(decoded.getAudience()).containsExactly(AUDIENCE);
            assertThat(decoded.getIssuedAt()).isEqualTo(ISSUED_AT);
            assertThat(decoded.getNotBefore()).isEqualTo(ISSUED_AT);
            assertThat(decoded.getExpiresAt()).isEqualTo(expiresAt);
            assertThat(decoded.getId()).isEqualTo(TOKEN_ID);
            assertThat(decoded.getClaimAsString("sid")).isEqualTo(SESSION_ID);
            assertThat(context.getBean(KeyPair.class)).isSameAs(keyPair);
        });
    }

    @Test
    void unrelatedSignatureAndRs512AreRejectedBeforeEnvelopeValidation() {
        contextRunner.run(context -> {
            var localKeyPair = context.getBean(KeyPair.class);
            var decoder = context.getBean(JwtDecoder.class);
            var claims = validClaims(ISSUED_AT, ISSUED_AT.plus(ACCESS_TOKEN_LIFETIME));
            var unrelatedToken = sign(newRsaKeyPair(), JWSAlgorithm.RS256, validHeaders(), claims);
            clock.reset(FRACTIONAL_OBSERVED_AT);

            assertThatThrownBy(() -> decoder.decode(unrelatedToken)).isInstanceOf(JwtException.class);
            assertThat(clock.invocations()).isZero();

            var rs512Token = sign(localKeyPair, JWSAlgorithm.RS512, validHeaders(), claims);
            clock.reset(FRACTIONAL_OBSERVED_AT);

            assertThatThrownBy(() -> decoder.decode(rs512Token)).isInstanceOf(JwtException.class);
            assertThat(clock.invocations()).isZero();
        });
    }

    @Test
    void everyHeaderAndClaimEnvelopeFailureUsesOneSafeStableError() {
        contextRunner.run(context -> {
            var keyPair = context.getBean(KeyPair.class);
            var decoder = context.getBean(JwtDecoder.class);

            invalidEnvelopes()
                    .forEach(envelope -> assertEnvelopeRejected(
                            decoder,
                            keyPair,
                            envelope.headers(),
                            envelope.claims(),
                            FRACTIONAL_OBSERVED_AT,
                            envelope.name()));

            assertRejectedBeforeEnvelopeValidation(decoder, keyPair, validClaims(ISSUED_AT, ISSUED_AT));
            assertRejectedBeforeEnvelopeValidation(decoder, keyPair, validClaims(ISSUED_AT, ISSUED_AT.minusSeconds(1)));
        });
    }

    @Test
    void exactTimeBoundariesUseTheInjectedClockOnceWithoutSkew() {
        contextRunner.run(context -> {
            var keyPair = context.getBean(KeyPair.class);
            var decoder = context.getBean(JwtDecoder.class);
            var exactNow = Instant.parse("2026-08-09T15:00:00Z");
            var exactNotBefore =
                    sign(keyPair, JWSAlgorithm.RS256, validHeaders(), validClaims(exactNow, exactNow.plusSeconds(60)));
            clock.reset(exactNow);

            assertThat(decoder.decode(exactNotBefore).getNotBefore()).isEqualTo(exactNow);
            assertThat(clock.invocations()).isOne();

            var oneSecondWindow =
                    sign(keyPair, JWSAlgorithm.RS256, validHeaders(), validClaims(ISSUED_AT, ISSUED_AT.plusSeconds(1)));
            clock.reset(FRACTIONAL_OBSERVED_AT);

            assertThat(decoder.decode(oneSecondWindow).getExpiresAt()).isEqualTo(ISSUED_AT.plusSeconds(1));
            assertThat(clock.invocations()).isOne();

            var expiresExactlyNow = validClaims(exactNow.minusSeconds(60), exactNow);
            assertEnvelopeRejected(
                    decoder, keyPair, validHeaders(), expiresExactlyNow, exactNow, "expiry equality boundary");

            var futureNotBefore = validClaims(exactNow.plusSeconds(1), exactNow.plusSeconds(61));
            assertEnvelopeRejected(
                    decoder, keyPair, validHeaders(), futureNotBefore, exactNow, "future not-before boundary");
        });
    }

    private String encode(JwtEncoder encoder, Instant issuedAt, Instant expiresAt) {
        var headers = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(KEY_ID)
                .type("access")
                .build();
        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(SUBJECT)
                .audience(List.of(AUDIENCE))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(TOKEN_ID)
                .claim("sid", SESSION_ID)
                .build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    private List<InvalidEnvelope> invalidEnvelopes() {
        var fractionalIssuedAt = BigDecimal.valueOf(ISSUED_AT.getEpochSecond()).add(new BigDecimal("0.5"));
        var fractionalExpiresAt =
                BigDecimal.valueOf(ISSUED_AT.plusSeconds(60).getEpochSecond()).add(new BigDecimal("0.5"));
        return List.of(
                invalid("missing type", headers -> headers.remove("typ"), claims -> {}),
                invalid("wrong type", headers -> headers.put("typ", "refresh"), claims -> {}),
                invalid("missing key ID", headers -> headers.remove("kid"), claims -> {}),
                invalid("wrong key ID", headers -> headers.put("kid", "wrong-key"), claims -> {}),
                invalid("missing issuer", headers -> {}, claims -> claims.remove("iss")),
                invalid("wrong issuer", headers -> {}, claims -> claims.put("iss", "https://wrong.example")),
                invalid("missing audience", headers -> {}, claims -> claims.remove("aud")),
                invalid("wrong audience", headers -> {}, claims -> claims.put("aud", "wrong-audience")),
                invalid(
                        "multiple audiences",
                        headers -> {},
                        claims -> claims.put("aud", List.of(AUDIENCE, "second-audience"))),
                invalid("missing subject", headers -> {}, claims -> claims.remove("sub")),
                invalid("malformed subject", headers -> {}, claims -> claims.put("sub", "not-a-uuid")),
                invalid(
                        "non-canonical subject",
                        headers -> {},
                        claims -> claims.put("sub", SUBJECT.toUpperCase(Locale.ROOT))),
                invalid("missing token ID", headers -> {}, claims -> claims.remove("jti")),
                invalid("malformed token ID", headers -> {}, claims -> claims.put("jti", "not-a-uuid")),
                invalid(
                        "non-canonical token ID",
                        headers -> {},
                        claims -> claims.put("jti", TOKEN_ID.toUpperCase(Locale.ROOT))),
                invalid("missing session ID", headers -> {}, claims -> claims.remove("sid")),
                invalid("malformed session ID", headers -> {}, claims -> claims.put("sid", "not-a-uuid")),
                invalid(
                        "non-canonical session ID",
                        headers -> {},
                        claims -> claims.put("sid", SESSION_ID.toUpperCase(Locale.ROOT))),
                invalid("missing issued-at", headers -> {}, claims -> claims.remove("iat")),
                invalid("missing not-before", headers -> {}, claims -> claims.remove("nbf")),
                invalid("missing expiry", headers -> {}, claims -> claims.remove("exp")),
                invalid("fractional issued-at", headers -> {}, claims -> claims.put("iat", fractionalIssuedAt)),
                invalid("fractional not-before", headers -> {}, claims -> claims.put("nbf", fractionalIssuedAt)),
                invalid("fractional expiry", headers -> {}, claims -> claims.put("exp", fractionalExpiresAt)),
                invalid(
                        "unequal issued-at and not-before",
                        headers -> {},
                        claims -> claims.put("nbf", ISSUED_AT.plusSeconds(1).getEpochSecond())),
                invalid(
                        "overlong validity window",
                        headers -> {},
                        claims -> claims.put(
                                "exp",
                                ISSUED_AT
                                        .plus(ACCESS_TOKEN_LIFETIME)
                                        .plusSeconds(1)
                                        .getEpochSecond())));
    }

    private InvalidEnvelope invalid(
            String name, Consumer<Map<String, Object>> headerChange, Consumer<Map<String, Object>> claimChange) {
        var headers = validHeaders();
        var claims = validClaims(ISSUED_AT, ISSUED_AT.plus(ACCESS_TOKEN_LIFETIME));
        headerChange.accept(headers);
        claimChange.accept(claims);
        return new InvalidEnvelope(name, Map.copyOf(headers), Map.copyOf(claims));
    }

    private Map<String, Object> validHeaders() {
        var headers = new LinkedHashMap<String, Object>();
        headers.put("kid", KEY_ID);
        headers.put("typ", "access");
        return headers;
    }

    private Map<String, Object> validClaims(Instant issuedAt, Instant expiresAt) {
        var claims = new LinkedHashMap<String, Object>();
        claims.put("iss", ISSUER);
        claims.put("sub", SUBJECT);
        claims.put("aud", AUDIENCE);
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("nbf", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("jti", TOKEN_ID);
        claims.put("sid", SESSION_ID);
        return claims;
    }

    private String sign(
            KeyPair keyPair, JWSAlgorithm algorithm, Map<String, Object> headers, Map<String, Object> claims) {
        var headerBuilder = new JWSHeader.Builder(algorithm);
        if (headers.get("kid") instanceof String keyId) {
            headerBuilder.keyID(keyId);
        }
        if (headers.get("typ") instanceof String type) {
            headerBuilder.type(new JOSEObjectType(type));
        }
        var signedJwt = new JWSObject(headerBuilder.build(), new Payload(JSONObjectUtils.toJSONString(claims)));
        try {
            signedJwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to construct test token", exception);
        }
        return signedJwt.serialize();
    }

    private void assertEnvelopeRejected(
            JwtDecoder decoder,
            KeyPair keyPair,
            Map<String, Object> headers,
            Map<String, Object> claims,
            Instant observedAt,
            String caseName) {
        var compactToken = sign(keyPair, JWSAlgorithm.RS256, headers, claims);
        clock.reset(observedAt);

        var thrown = catchThrowable(() -> decoder.decode(compactToken));

        assertThat(thrown).as(caseName).isExactlyInstanceOf(JwtValidationException.class);
        assertThat(clock.invocations()).as(caseName).isOne();
        var validationException = (JwtValidationException) thrown;
        assertThat(validationException.getErrors()).as(caseName).hasSize(1);
        var error = validationException.getErrors().iterator().next();
        assertThat(error.getErrorCode()).as(caseName).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
        assertThat(error.getDescription()).as(caseName).isEqualTo(LocalAccessTokenValidator.INVALID_TOKEN_DESCRIPTION);
        assertThat(error.getUri()).as(caseName).isNull();
        assertThat(validationException.getMessage())
                .as(caseName)
                .doesNotContain(
                        compactToken,
                        caseName,
                        KEY_ID,
                        ISSUER,
                        AUDIENCE,
                        SUBJECT,
                        TOKEN_ID,
                        SESSION_ID,
                        "wrong-key",
                        "wrong-audience",
                        "https://wrong.example",
                        "not-a-uuid",
                        "refresh");
    }

    private void assertRejectedBeforeEnvelopeValidation(
            JwtDecoder decoder, KeyPair keyPair, Map<String, Object> claims) {
        var compactToken = sign(keyPair, JWSAlgorithm.RS256, validHeaders(), claims);
        clock.reset(FRACTIONAL_OBSERVED_AT);

        assertThatThrownBy(() -> decoder.decode(compactToken)).isInstanceOf(JwtException.class);
        assertThat(clock.invocations()).isZero();
    }

    private KeyPair newRsaKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("RSA is unavailable for the test", exception);
        }
    }

    private record InvalidEnvelope(String name, Map<String, Object> headers, Map<String, Object> claims) {}

    private static final class RecordingClock extends Clock {

        private Instant instant;
        private int invocations;

        private RecordingClock(Instant instant) {
            this.instant = instant;
        }

        void reset(Instant instant) {
            this.instant = instant;
            invocations = 0;
        }

        int invocations() {
            return invocations;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            invocations++;
            return instant;
        }
    }
}
