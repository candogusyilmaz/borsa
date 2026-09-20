package dev.canverse.stocks.testing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.springframework.test.web.servlet.MvcResult;

public final class HttpAssertions {

    private HttpAssertions() {}

    public static void assertProblem(MvcResult result) throws Exception {
        var body = JsonPath.<Map<String, Object>>read(result.getResponse().getContentAsString(), "$");
        assertThat(body).containsKeys("type", "title", "status", "instance", "code", "key", "traceId", "timestamp");
    }

    public static void assertSlice(MvcResult result) throws Exception {
        var body = JsonPath.<Map<String, Object>>read(result.getResponse().getContentAsString(), "$");
        assertThat(body).containsOnlyKeys("items", "page", "size", "hasNext");
    }
}
