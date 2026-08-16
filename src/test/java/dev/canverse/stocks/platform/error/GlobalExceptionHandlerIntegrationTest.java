package dev.canverse.stocks.platform.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerIntegrationTest {

    private static final Instant TEST_TIME = Instant.parse("2026-08-08T12:00:00Z");

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorHandlingTestController())
                .setControllerAdvice(new GlobalExceptionHandler(Clock.fixed(TEST_TIME, ZoneOffset.UTC), Tracer.NOOP))
                .addFilters(new RequestTraceFilter(() -> UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
                .build();
    }

    @Test
    void applicationExceptionProducesProblemDetailAndCorrelatesTraceId() throws Exception {
        var result = mockMvc.perform(
                        get("/test/errors/application").header(RequestTraceFilter.TRACE_ID_HEADER, "client-supplied"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://canverse.dev/problems/entity-not-found"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/test/errors/application"))
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.key").value("error.common.entity_not_found"))
                .andExpect(jsonPath("$.params.entity").value("Example"))
                .andExpect(jsonPath("$.params.id").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.timestamp").value(TEST_TIME.toString()))
                .andExpect(header().exists(RequestTraceFilter.TRACE_ID_HEADER))
                .andReturn();

        var headerTraceId = result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER);
        var bodyTraceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");
        assertThat(headerTraceId).isNotBlank().isEqualTo(bodyTraceId).isNotEqualTo("client-supplied");
    }

    @Test
    void internalApplicationExceptionDoesNotExposeParamsOrMessage() throws Exception {
        var response = mockMvc.perform(get("/test/errors/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.key").value("error.common.internal_error"))
                .andExpect(jsonPath("$.params").doesNotExist())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("database password", "secret");
    }

    @Test
    void unhandledExceptionDoesNotExposeInternalDetails() throws Exception {
        var response = mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.params").doesNotExist())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("internal secret exception message", "IllegalStateException", "stackTrace");
    }

    @Test
    void beanValidationUsesTheCommonValidationShape() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.errors[0].field").value("email"))
                .andExpect(jsonPath("$.params.errors[0].key").value("error.fields.common.not_blank"))
                .andExpect(jsonPath("$.params.errors[0].detail").value("must not be blank"));
    }

    @Test
    void methodParameterValidationUsesTheCommonValidationShape() throws Exception {
        mockMvc.perform(get("/test/errors/parameter-validation").queryParam("value", "1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.errors[0].field").value("value"))
                .andExpect(jsonPath("$.params.errors[0].key").value("error.fields.common.min"));
    }

    @Test
    void malformedJsonUsesStableCodeWithoutParserMessage() throws Exception {
        var response = mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("Unexpected end-of-input", "JsonParseException");
    }

    @Test
    void missingRequestParameterUsesSafeParameterName() throws Exception {
        mockMvc.perform(get("/test/errors/missing"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_VALUE"))
                .andExpect(jsonPath("$.params.parameter").value("parameter"));
    }

    @Test
    void methodNotAllowedUsesStableCode() throws Exception {
        mockMvc.perform(post("/test/errors/application"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedMediaTypeUsesStableCode() throws Exception {
        mockMvc.perform(post("/test/errors/media")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unexpectedPersistenceExceptionUsesGenericServerError() throws Exception {
        var response = mockMvc.perform(get("/test/errors/conflict"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.params").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("secret_database_constraint", "constraint=");
    }

    @Test
    void knownPersistenceConstraintUsesTheCapabilityErrorCodeWithoutLeakingDatabaseDetails() throws Exception {
        var response = mockMvc.perform(get("/test/errors/known-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.key").value("error.identity.email_already_registered"))
                .andExpect(jsonPath("$.params").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("uq_user_account_email_normalized", "secret sql", "duplicate key");
    }

    @Test
    void optimisticLockFailureUsesTheExistingStateConflictContract() throws Exception {
        mockMvc.perform(get("/test/errors/optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.key").value("error.common.state_conflict"));
    }
}
