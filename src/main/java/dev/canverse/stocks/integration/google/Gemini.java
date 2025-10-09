package dev.canverse.stocks.integration.google;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class Gemini {

    @Bean
    public Client geminiClient(@Value("${app.gemini-api-key}") String apiKey) {
        return Client.builder()
                .apiKey(apiKey)
                .build();
    }
}
