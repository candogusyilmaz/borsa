package dev.canverse.stocks.identity.configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
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
    KeyPair localAccessTokenKeyPair() {
        try {
            var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("RSA is unavailable", exception);
        }
    }

    @Bean
    JwtEncoder localAccessTokenEncoder(KeyPair localAccessTokenKeyPair, AccessTokenProperties properties) {
        return NimbusJwtEncoder.withKeyPair((RSAPublicKey) localAccessTokenKeyPair.getPublic(), (RSAPrivateKey)
                        localAccessTokenKeyPair.getPrivate())
                .algorithm(SignatureAlgorithm.RS256)
                .jwkPostProcessor(builder -> builder.keyID(properties.keyId()))
                .build();
    }

    @Bean
    JwtDecoder localAccessTokenDecoder(KeyPair localAccessTokenKeyPair, AccessTokenProperties properties, Clock clock) {
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) localAccessTokenKeyPair.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .validateType(false)
                .build();
        decoder.setJwtValidator(new LocalAccessTokenValidator(properties, clock));
        return decoder;
    }
}
