package dev.canverse.stocks.platform.web.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.CommonErrorCode;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "management.tracing.sampling.probability=1.0")
@AutoConfigureMockMvc
@Testcontainers
@Import(MicrometerTracingHttpTest.TestOverrides.class)
class MicrometerTracingHttpTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final String INBOUND_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String INBOUND_SPAN_ID = "00f067aa0ba902b7";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    MockMvc mockMvc;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void inboundTraceparentContinuesW3cTraceAndGeneratedRequestsAreIsolated() throws Exception {
        var inbound = probe(get("/test/tracing/probe").header("traceparent", TRACEPARENT));

        assertThat(inbound.nativeTraceId()).isEqualTo(INBOUND_TRACE_ID);
        assertThat(inbound.nativeSpanId()).hasSize(16).isNotEqualTo(INBOUND_SPAN_ID);
        assertThat(inbound.mdcTraceId()).isEqualTo(INBOUND_TRACE_ID);
        assertThat(inbound.mdcSpanId()).isEqualTo(inbound.nativeSpanId());
        assertMdcIsClean();

        var first = probe(get("/test/tracing/probe"));
        var second = probe(get("/test/tracing/probe"));
        assertThat(first.nativeTraceId()).hasSize(32);
        assertThat(second.nativeTraceId()).hasSize(32).isNotEqualTo(first.nativeTraceId());
        assertThat(first.compatibilityTraceId()).isNotEqualTo(second.compatibilityTraceId());
        assertThat(UUID.fromString(first.compatibilityTraceId())).isNotNull();
        assertThat(UUID.fromString(second.compatibilityTraceId())).isNotNull();
        assertMdcIsClean();
    }

    @Test
    void compatibilityHeaderAndProblemDetailsRetainTheExistingCorrelationContract() throws Exception {
        var result = mockMvc.perform(get("/test/tracing/error")).andExpect(status().isInternalServerError()).andReturn();

        var headerTraceId = result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER);
        var bodyTraceId = JsonPath.<String>read(result.getResponse().getContentAsString(), "$.traceId");
        assertThat(headerTraceId).isNotBlank().isEqualTo(bodyTraceId);
        assertThat(UUID.fromString(headerTraceId)).isNotNull();
        assertMdcIsClean();
    }

    private TraceProbeResponse probe(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        var result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        var body = result.getResponse().getContentAsString();
        var response = new TraceProbeResponse(JsonPath.read(body, "$.nativeTraceId"), JsonPath.read(body, "$.nativeSpanId"),
                JsonPath.read(body, "$.mdcTraceId"), JsonPath.read(body, "$.mdcSpanId"), JsonPath.read(body, "$.compatibilityTraceId"));
        assertThat(result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER)).isNotBlank();
        assertThat(response.compatibilityTraceId()).isEqualTo(result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER));
        return response;
    }

    private static void assertMdcIsClean() {
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
        assertThat(MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    private record TraceProbeResponse(String nativeTraceId, String nativeSpanId, String mdcTraceId, String mdcSpanId, String compatibilityTraceId) {}

    @RestController
    static class TraceProbeController {

        private final Tracer tracer;

        TraceProbeController(Tracer tracer) {
            this.tracer = tracer;
        }

        @GetMapping("/test/tracing/probe")
        TraceProbeResponse probe() {
            var span = tracer.currentSpan();
            return new TraceProbeResponse(spanValue(span, true), spanValue(span, false), valueOrNone(MDC.get("traceId")), valueOrNone(MDC.get("spanId")),
                    valueOrNone(MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY)));
        }

        @GetMapping("/test/tracing/error")
        void error() {
            throw new AppException(CommonErrorCode.INTERNAL_ERROR, Map.of("detail", "trace probe failure"));
        }

        private static String spanValue(Span span, boolean traceId) {
            if (span == null) {
                return "none";
            }
            return traceId ? span.context().traceId() : span.context().spanId();
        }

        private static String valueOrNone(String value) {
            return value == null ? "none" : value;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {

        @Bean
        TraceProbeController traceProbeController(Tracer tracer) {
            return new TraceProbeController(tracer);
        }
    }
}
