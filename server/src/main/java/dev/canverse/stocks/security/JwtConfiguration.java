package dev.canverse.stocks.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtConfiguration {
    public static final String ISSUER = "finance-api";
    public static final String TOKEN_USE_CLAIM = "token_use";
    public static final String ACCESS_TOKEN_USE = "access";
    public static final String REFRESH_TOKEN_USE = "refresh";

    private final RsaKeyProperties rsaKeys;

    @Bean
    public JwtDecoder accessTokenDecoder() {
        return decoderFor(ACCESS_TOKEN_USE);
    }

    @Bean
    public JwtDecoder refreshTokenDecoder() {
        return decoderFor(REFRESH_TOKEN_USE);
    }

    private JwtDecoder decoderFor(String tokenUse) {
        var decoder = NimbusJwtDecoder.withPublicKey(rsaKeys.getPublicKey()).build();
        decoder.setJwtValidator(
                JwtValidators.createDefaultWithValidators(
                        List.of(
                                new JwtIssuerValidator(ISSUER),
                                new JwtClaimValidator<>(TOKEN_USE_CLAIM, tokenUse::equals))));
        return decoder;
    }

    @Bean
    public JwtEncoder encoder() {
        var jwk =
                new RSAKey.Builder(rsaKeys.getPublicKey())
                        .privateKey(rsaKeys.getPrivateKey())
                        .build();
        var jwks = new ImmutableJWKSet<>(new JWKSet(jwk));

        return new NimbusJwtEncoder(jwks);
    }
}
