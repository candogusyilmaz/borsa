package dev.canverse.stocks.platform.web.trace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.platform.id.IdGenerator;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTraceFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.remove(RequestTraceFilter.TRACE_ID_MDC_KEY);
    }

    @Test
    void exposesTraceIdToTheRequestAndMdcOnlyDuringFilterChain() throws Exception {
        var traceId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        IdGenerator idGenerator = () -> traceId;
        var filter = new RequestTraceFilter(idGenerator);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            assertThat(MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY)).isEqualTo(traceId.toString());
            assertThat(request.getAttribute(RequestTraceFilter.TRACE_ID_ATTRIBUTE)).isEqualTo(traceId.toString());
        });

        assertThat(response.getHeader(RequestTraceFilter.TRACE_ID_HEADER)).isEqualTo(traceId.toString());
        assertThat(MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY)).isNull();
    }
}
