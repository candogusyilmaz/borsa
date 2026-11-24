package dev.canverse.stocks.service.account;

import com.fasterxml.jackson.annotation.JsonProperty;

import dev.canverse.stocks.domain.exception.BadRequestException;
import dev.canverse.stocks.security.GoogleOAuthProperties;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Service
public class GoogleAuthService {
    private static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private final GoogleOAuthProperties properties;
    private final RestClient restClient;
    private final JwtDecoder jwtDecoder;

    public GoogleAuthService(
            GoogleOAuthProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.jwtDecoder = buildGoogleJwtDecoder(properties.clientId());
    }

    public GoogleUserInfo verifyAuthorizationCode(String code) {
        if (code == null || code.isBlank()) {
            log.atWarn()
                    .addKeyValue("event", "authentication_failed")
                    .addKeyValue("authMethod", "google")
                    .addKeyValue("reason", "missing_code")
                    .log("Google authentication failed");
            throw new BadRequestException("Google authorization code is required");
        }

        var idToken = exchangeCodeForIdToken(code);
        var jwt = decodeAndValidate(idToken);

        var email = jwt.getClaimAsString("email");
        var emailVerified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
        if (email == null || email.isBlank() || !emailVerified) {
            log.atWarn()
                    .addKeyValue("event", "authentication_failed")
                    .addKeyValue("authMethod", "google")
                    .addKeyValue("reason", "email_not_verified")
                    .log("Google authentication failed");
            throw new BadRequestException("Google account email is not verified");
        }

        var name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = email.substring(0, email.indexOf('@'));
        }

        return new GoogleUserInfo(email, name);
    }

    private String exchangeCodeForIdToken(String code) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", "postmessage");
        form.add("grant_type", "authorization_code");

        GoogleTokenExchangeResponse response;
        try {
            response =
                    restClient
                            .post()
                            .uri(GOOGLE_TOKEN_URI)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(GoogleTokenExchangeResponse.class);
        } catch (RestClientException e) {
            log.atWarn()
                    .addKeyValue("event", "authentication_failed")
                    .addKeyValue("authMethod", "google")
                    .addKeyValue("provider", "google_oauth")
                    .addKeyValue("reason", "token_exchange_failed")
                    .addKeyValue("exception", e.getClass().getSimpleName())
                    .log("Google token exchange failed");
            throw new BadRequestException("Failed to exchange Google authorization code");
        }

        if (response == null || response.idToken() == null || response.idToken().isBlank()) {
            log.atWarn()
                    .addKeyValue("event", "authentication_failed")
                    .addKeyValue("authMethod", "google")
                    .addKeyValue("provider", "google_oauth")
                    .addKeyValue("reason", "missing_id_token")
                    .log("Google token exchange returned no ID token");
            throw new BadRequestException("Google ID token was not returned");
        }

        return response.idToken();
    }

    private Jwt decodeAndValidate(String idToken) {
        try {
            return jwtDecoder.decode(idToken);
        } catch (JwtException e) {
            log.atWarn()
                    .addKeyValue("event", "authentication_failed")
                    .addKeyValue("authMethod", "google")
                    .addKeyValue("reason", "invalid_id_token")
                    .log("Google ID token validation failed");
            throw new BadRequestException("Invalid Google ID token");
        }
    }

    private static JwtDecoder buildGoogleJwtDecoder(String clientId) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();

        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefault(),
                        new JwtClaimValidator<Object>(
                                "iss",
                                iss ->
                                        iss != null
                                                && ("https://accounts.google.com"
                                                                .equals(iss.toString())
                                                        || "accounts.google.com"
                                                                .equals(iss.toString()))),
                        new JwtClaimValidator<List<String>>(
                                "aud", aud -> aud != null && aud.contains(clientId)));

        decoder.setJwtValidator(validator);
        return decoder;
    }

    public record GoogleUserInfo(String email, String name) {}

    private record GoogleTokenExchangeResponse(@JsonProperty("id_token") String idToken) {}
}
