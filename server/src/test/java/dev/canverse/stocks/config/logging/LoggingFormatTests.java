package dev.canverse.stocks.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.logging.logback.ColorConverter;
import org.springframework.boot.logging.logback.EnclosedInSquareBracketsConverter;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

class LoggingFormatTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void devConsolePatternRendersCompactReadableLogsWithAndWithoutRequestIdAndKvp() {
        Properties baseProps = loadYaml("application.yml");
        String pattern = baseProps.getProperty("logging.pattern.console");
        assertThat(pattern).isNotBlank();
        assertThat(baseProps.getProperty("spring.jpa.show-sql")).isEqualTo("false");

        AnsiOutput.setEnabled(AnsiOutput.Enabled.NEVER);
        try {
            LoggerContext context = new LoggerContext();
            context.putObject(
                    ch.qos.logback.core.CoreConstants.PATTERN_RULE_REGISTRY,
                    Map.of(
                            "clr", ColorConverter.class.getName(),
                            "esb", EnclosedInSquareBracketsConverter.class.getName(),
                            "wEx", ExtendedWhitespaceThrowableProxyConverter.class.getName()));

            PatternLayout layout = new PatternLayout();
            layout.setContext(context);
            layout.setPattern(pattern);
            layout.start();

            Logger httpLogger = context.getLogger("dev.canverse.stocks.config.logging.HTTP");
            LoggingEvent httpEvent =
                    new LoggingEvent(
                            Logger.FQCN,
                            httpLogger,
                            Level.INFO,
                            "GET /api/portfolios 200 42ms",
                            null,
                            null);
            httpEvent.setMDCPropertyMap(Map.of("requestId", "a83f2b91", "userId", "42"));
            httpEvent.addKeyValuePair(new KeyValuePair("event", "http_request_completed"));
            httpEvent.addKeyValuePair(new KeyValuePair("method", "GET"));
            httpEvent.addKeyValuePair(new KeyValuePair("route", "/api/portfolios"));
            httpEvent.addKeyValuePair(new KeyValuePair("status", 200));
            httpEvent.addKeyValuePair(new KeyValuePair("durationMs", 42));

            String renderedHttp = layout.doLayout(httpEvent).replace("\r", "").replace("\n", "");
            assertThat(renderedHttp)
                    .matches(
                            "^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} INFO  \\[a83f2b91] HTTP - GET /api/portfolios 200 42ms event=http_request_completed method=GET route=/api/portfolios status=200 durationMs=42$");

            Logger updaterLogger =
                    context.getLogger(
                            "dev.canverse.stocks.service.marketupdater.MarketQuoteFetcher");
            LoggingEvent warnEvent =
                    new LoggingEvent(
                            Logger.FQCN, updaterLogger, Level.WARN, "Provider timeout", null, null);
            warnEvent.setMDCPropertyMap(Map.of());
            warnEvent.addKeyValuePair(new KeyValuePair("provider", "TradingView"));
            warnEvent.addKeyValuePair(new KeyValuePair("market", "BIST"));

            String renderedWarn = layout.doLayout(warnEvent).replace("\r", "").replace("\n", "");
            assertThat(renderedWarn)
                    .matches(
                            "^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} WARN  MarketQuoteFetcher - Provider timeout provider=TradingView market=BIST$");
            assertThat(renderedWarn).doesNotContain("[]");

            Logger startupLogger = context.getLogger("dev.canverse.stocks.Server");
            LoggingEvent plainEvent =
                    new LoggingEvent(
                            Logger.FQCN, startupLogger, Level.INFO, "Started Server", null, null);
            plainEvent.setMDCPropertyMap(Map.of());

            String renderedPlain = layout.doLayout(plainEvent).replace("\r", "").replace("\n", "");
            assertThat(renderedPlain)
                    .matches("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} INFO  Server - Started Server$");
        } finally {
            AnsiOutput.setEnabled(AnsiOutput.Enabled.DETECT);
        }
    }

    @Test
    void prodEcsConfigurationProducesValidStructuredJsonWithRequiredFields() throws Exception {
        Properties baseProps = loadYaml("application.yml");
        Properties prodProps = loadYaml("application-prod.yml");

        assertThat(prodProps.getProperty("spring.jpa.show-sql")).isEqualTo("false");
        assertThat(prodProps.getProperty("logging.structured.format.console")).isEqualTo("ecs");

        StandardEnvironment env = new StandardEnvironment();
        Properties overrides = new Properties();
        overrides.setProperty("APP_VERSION", "7f3a9c2");
        env.getPropertySources().addFirst(new PropertiesPropertySource("overrides", overrides));
        env.getPropertySources().addLast(new PropertiesPropertySource("prod", prodProps));
        env.getPropertySources().addLast(new PropertiesPropertySource("base", baseProps));

        LoggerContext context = new LoggerContext();
        context.putObject(Environment.class.getName(), env);

        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat(prodProps.getProperty("logging.structured.format.console"));
        encoder.start();

        Logger httpLogger = context.getLogger("dev.canverse.stocks.config.logging.HTTP");
        LoggingEvent httpEvent =
                new LoggingEvent(
                        Logger.FQCN,
                        httpLogger,
                        Level.INFO,
                        "POST /api/portfolios/{portfolioId}/trades/buy 200 62ms",
                        null,
                        null);
        httpEvent.setMDCPropertyMap(Map.of("requestId", "a83f2b91", "userId", "42"));
        httpEvent.addKeyValuePair(new KeyValuePair("event", "http_request_completed"));
        httpEvent.addKeyValuePair(new KeyValuePair("method", "POST"));
        httpEvent.addKeyValuePair(
                new KeyValuePair("route", "/api/portfolios/{portfolioId}/trades/buy"));
        httpEvent.addKeyValuePair(new KeyValuePair("status", 200));
        httpEvent.addKeyValuePair(new KeyValuePair("durationMs", 62));

        String json = new String(encoder.encode(httpEvent), StandardCharsets.UTF_8);
        assertThat(json.trim()).doesNotContain("\n");

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.path("@timestamp").asText()).isNotBlank();
        assertThat(node.path("log").path("level").asText()).isEqualTo("INFO");
        assertThat(node.path("log").path("logger").asText())
                .isEqualTo("dev.canverse.stocks.config.logging.HTTP");
        assertThat(node.path("service").path("name").asText()).isEqualTo("Finance");
        assertThat(node.path("service").path("environment").asText()).isEqualTo("prod");
        assertThat(node.path("service").path("version").asText()).isEqualTo("7f3a9c2");
        assertThat(node.path("message").asText())
                .isEqualTo("POST /api/portfolios/{portfolioId}/trades/buy 200 62ms");
        assertThat(node.path("requestId").asText()).isEqualTo("a83f2b91");
        assertThat(node.path("userId").asText()).isEqualTo("42");
        assertThat(node.path("event").asText()).isEqualTo("http_request_completed");
        assertThat(node.path("method").asText()).isEqualTo("POST");
        assertThat(node.path("route").asText())
                .isEqualTo("/api/portfolios/{portfolioId}/trades/buy");
        assertThat(node.path("status").asInt()).isEqualTo(200);
        assertThat(node.path("durationMs").asLong()).isEqualTo(62L);
        assertThat(node.path("ecs").path("version").asText()).isNotBlank();

        LoggingEvent errorEvent =
                new LoggingEvent(
                        Logger.FQCN,
                        context.getLogger("dev.canverse.stocks.rest.ExceptionHandlerAdvice"),
                        Level.ERROR,
                        "Unhandled server exception",
                        new IllegalStateException("Unexpected failure"),
                        null);
        errorEvent.setMDCPropertyMap(Map.of("requestId", "c91d2a13", "userId", "42"));
        errorEvent.addKeyValuePair(new KeyValuePair("event", "unhandled_exception"));
        errorEvent.addKeyValuePair(new KeyValuePair("category", "application"));

        String errorJson = new String(encoder.encode(errorEvent), StandardCharsets.UTF_8);
        JsonNode errorNode = objectMapper.readTree(errorJson);
        assertThat(errorNode.path("log").path("level").asText()).isEqualTo("ERROR");
        assertThat(errorNode.path("error").path("type").asText())
                .isEqualTo("java.lang.IllegalStateException");
        assertThat(errorNode.path("error").path("stack_trace").asText())
                .contains("IllegalStateException");

        encoder.stop();
    }

    private static Properties loadYaml(String path) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(path));
        Properties props = yaml.getObject();
        return props != null ? props : new Properties();
    }
}
