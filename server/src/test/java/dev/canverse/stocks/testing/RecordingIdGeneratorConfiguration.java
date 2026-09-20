package dev.canverse.stocks.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class RecordingIdGeneratorConfiguration {

    @Bean
    @Primary
    RecordingIdGenerator recordingIdGenerator() {
        return new RecordingIdGenerator();
    }
}
