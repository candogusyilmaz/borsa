package dev.canverse.stocks.identity.configuration;

import dev.canverse.stocks.identity.application.LocalAccessTokenAuthenticationConverter;
import dev.canverse.stocks.identity.web.LocalBearerAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiBearerSecurityConfiguration {

    @Bean
    LocalBearerAuthenticationEntryPoint localBearerAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        return new LocalBearerAuthenticationEntryPoint(handlerExceptionResolver);
    }

    @Bean
    SecurityFilterChain apiBearerSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder, LocalAccessTokenAuthenticationConverter authenticationConverter,
            LocalBearerAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        http.securityMatcher("/api/v1/**").sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll().requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
                .oauth2ResourceServer(resourceServer -> resourceServer.authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authenticationConverter)));
        return http.build();
    }
}
