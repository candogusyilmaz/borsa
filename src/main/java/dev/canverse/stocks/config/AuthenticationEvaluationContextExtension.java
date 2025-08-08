package dev.canverse.stocks.config;

import dev.canverse.stocks.domain.entity.account.User;
import dev.canverse.stocks.security.AuthenticationProvider;
import org.springframework.data.spel.spi.EvaluationContextExtension;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEvaluationContextExtension implements EvaluationContextExtension {

    @Override
    public String getExtensionId() {
        return "principal";
    }

    @Override
    public User getRootObject() {
        return AuthenticationProvider.getUser();
    }
}