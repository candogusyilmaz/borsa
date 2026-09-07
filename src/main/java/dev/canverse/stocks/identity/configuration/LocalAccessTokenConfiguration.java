package dev.canverse.stocks.identity.configuration;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccessTokenProperties.class)
public class LocalAccessTokenConfiguration {

    @Bean
    KeyPair localAccessTokenKeyPair(AccessTokenProperties properties) {
        if (properties.privateKeyPem() != null) {
            return parseConfiguredKeyPair(properties.privateKeyPem(), properties.publicKeyPem());
        }
        return generateEphemeralKeyPair();
    }

    private KeyPair generateEphemeralKeyPair() {
        try {
            var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("RSA is unavailable", exception);
        }
    }

    private KeyPair parseConfiguredKeyPair(String privateKeyPem, String publicKeyPem) {
        try {
            var keyFactory = KeyFactory.getInstance("RSA");
            var privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(privateKeyPem, "PRIVATE KEY")));
            var publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(decodePem(publicKeyPem, "PUBLIC KEY")));
            if (!(privateKey instanceof RSAPrivateKey rsaPrivateKey) || !(publicKey instanceof RSAPublicKey rsaPublicKey)) {
                throw new IllegalArgumentException("Configured access-token keys must be RSA keys");
            }
            if (!rsaPrivateKey.getModulus().equals(rsaPublicKey.getModulus())) {
                throw new IllegalArgumentException("Configured access-token keys do not belong to the same pair");
            }
            return new KeyPair(rsaPublicKey, rsaPrivateKey);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Configured access-token RSA keys are invalid", exception);
        }
    }

    private byte[] decodePem(String pem, String type) {
        var begin = "-----BEGIN " + type + "-----";
        var end = "-----END " + type + "-----";
        var normalized = pem.replace("\\n", "\n").replace("\r", "").trim();
        if (!normalized.startsWith(begin) || !normalized.endsWith(end)) {
            throw new IllegalArgumentException("Expected PEM encoded " + type);
        }
        var encoded = normalized.substring(begin.length(), normalized.length() - end.length());
        try {
            return Base64.getMimeDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("PEM encoded " + type + " is not valid Base64", exception);
        }
    }

    @Bean
    JwtEncoder localAccessTokenEncoder(KeyPair localAccessTokenKeyPair, AccessTokenProperties properties) {
        return NimbusJwtEncoder.withKeyPair((RSAPublicKey) localAccessTokenKeyPair.getPublic(), (RSAPrivateKey) localAccessTokenKeyPair.getPrivate())
                .algorithm(SignatureAlgorithm.RS256).jwkPostProcessor(builder -> builder.keyID(properties.keyId())).build();
    }

    @Bean
    JwtDecoder localAccessTokenDecoder(KeyPair localAccessTokenKeyPair, AccessTokenProperties properties, Clock clock) {
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) localAccessTokenKeyPair.getPublic()).signatureAlgorithm(SignatureAlgorithm.RS256)
                .validateType(false).build();
        decoder.setJwtValidator(new LocalAccessTokenValidator(properties, clock));
        return decoder;
    }
}
