package dev.canverse.stocks.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class OpenApiHttpTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    MockMvc mockMvc;

    @Test
    void publishesControllerContractsWithRequiredSliceProperties() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var document = JsonPath.parse(response);

        assertThat(document.<String>read("$.openapi")).isEqualTo("3.0.1");
        var paths = document.<Map<?, ?>>read("$.paths");
        assertThat(paths.containsKey("/api/v1/reference/instruments")).isTrue();
        assertThat(paths.keySet().stream().allMatch(path -> path.toString().startsWith("/api/v1/"))).isTrue();

        var schemas = document.<Map<?, ?>>read("$.components.schemas");
        var sliceSchemas = schemas.entrySet().stream().filter(entry -> entry.getKey().toString().startsWith("SliceResponse")).toList();
        assertThat(sliceSchemas).isNotEmpty();

        for (var entry : sliceSchemas) {
            assertThat(entry.getValue()).isInstanceOf(Map.class);
            var required = ((Map<?, ?>) entry.getValue()).get("required");
            assertThat(required).isInstanceOf(List.class);
            assertThat(((List<?>) required).containsAll(List.of("items", "page", "size", "hasNext"))).isTrue();
        }
    }
}
