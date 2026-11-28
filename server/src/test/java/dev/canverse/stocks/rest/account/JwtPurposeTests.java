package dev.canverse.stocks.rest.account;

import static dev.canverse.stocks.security.JwtConfiguration.ACCESS_TOKEN_USE;
import static dev.canverse.stocks.security.JwtConfiguration.ISSUER;
import static dev.canverse.stocks.security.JwtConfiguration.REFRESH_TOKEN_USE;
import static dev.canverse.stocks.security.JwtConfiguration.TOKEN_USE_CLAIM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.canverse.stocks.config.JwtAuthenticationConverter;
import dev.canverse.stocks.config.SecurityConfiguration;
import dev.canverse.stocks.domain.entity.account.User;
import dev.canverse.stocks.repository.UserRepository;
import dev.canverse.stocks.security.JwtConfiguration;
import dev.canverse.stocks.security.RsaKeyProperties;
import dev.canverse.stocks.service.account.GoogleAuthService;
import dev.canverse.stocks.service.account.TokenService;
import dev.canverse.stocks.service.account.UserService;
import dev.canverse.stocks.service.account.model.UserView;
import dev.canverse.stocks.service.portfolio.PositionService;
import jakarta.servlet.http.Cookie;
import java.net.HttpCookie;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitWebConfig(JwtPurposeTests.WebConfig.class)
class JwtPurposeTests {
    private static final String EMAIL = "user@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    @Qualifier("accessTokenDecoder")
    private JwtDecoder accessTokenDecoder;

    @Autowired
    @Qualifier("refreshTokenDecoder")
    private JwtDecoder refreshTokenDecoder;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private GoogleAuthService googleAuthService;
    @MockitoBean
    private PositionService positionService;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        user = new User(EMAIL, "hash");
        user.setId(7L);
        user.setName("Token test user");
        when(userRepository.findByEmailIncludePermissions(EMAIL)).thenReturn(Optional.of(user));
        when(userService.loadUserByUsername(EMAIL)).thenReturn(user);
        when(userService.getMe()).thenReturn(UserView.from(user));
    }

    @Test
    void accessTokenWorksAsBearerButNotAsRefreshCookie() throws Exception {
        var token = tokenService.createAccessToken(user).token();
        var jwt = accessTokenDecoder.decode(token);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));

        mockMvc.perform(accountRequest(token)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMAIL));
        mockMvc.perform(refreshRequest(token))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void refreshTokenWorksAsRefreshCookieButNotAsBearer() throws Exception {
        var cookie = HttpCookie.parse(tokenService.createRefreshTokenCookie(user)).getFirst();
        var token = cookie.getValue();
        var jwt = refreshTokenDecoder.decode(token);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofDays(7));
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7).toSeconds());

        mockMvc.perform(accountRequest(token)).andExpect(status().isUnauthorized());
        mockMvc.perform(refreshRequest(token))
            .andExpect(status().isOk())
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
            .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void rejectsTokensWithoutPurposeAtBothEndpoints() throws Exception {
        var token = sign(jwtEncoder, ISSUER, null, Instant.now().plusSeconds(300));

        mockMvc.perform(accountRequest(token)).andExpect(status().isUnauthorized());
        mockMvc.perform(refreshRequest(token)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {ACCESS_TOKEN_USE, REFRESH_TOKEN_USE})
    void preservesSignatureIssuerAndExpiryValidation(String tokenUse) throws Exception {
        var now = Instant.now();
        var anotherEncoder = new JwtConfiguration(WebConfig.rsaKeyProperties()).encoder();
        var invalidTokens = new String[]{sign(anotherEncoder, ISSUER, tokenUse, now.plusSeconds(300)),
            sign(jwtEncoder, "different-issuer", tokenUse, now.plusSeconds(300)),
            sign(jwtEncoder, ISSUER, tokenUse, now.minusSeconds(120))};

        for (String token : invalidTokens) {
            var request = ACCESS_TOKEN_USE.equals(tokenUse) ? accountRequest(token) : refreshRequest(token);
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
    }

    private static String sign(JwtEncoder encoder, String issuer, String tokenUse, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(EMAIL).issuedAt(expiresAt.minusSeconds(600)).expiresAt(expiresAt);
        if (tokenUse != null)
            claims.claim(TOKEN_USE_CLAIM, tokenUse);
        return encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }

    private static MockHttpServletRequestBuilder accountRequest(String token) {
        return get("/api/account/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder refreshRequest(String token) {
        return post("/api/auth/refresh-token").cookie(new Cookie("refresh-token", token));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfiguration.class, JwtConfiguration.class, JwtAuthenticationConverter.class, TokenService.class,
        TokenController.class, AccountController.class})
    static class WebConfig {
        @Bean
        static RsaKeyProperties rsaKeyProperties() throws NoSuchAlgorithmException {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            var properties = new RsaKeyProperties();
            properties.setPublicKey((RSAPublicKey) pair.getPublic());
            properties.setPrivateKey((RSAPrivateKey) pair.getPrivate());
            return properties;
        }
    }
}
