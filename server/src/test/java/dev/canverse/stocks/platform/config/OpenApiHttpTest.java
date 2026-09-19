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
        var pathNames = paths.keySet().stream().map(Object::toString).toList();
        assertThat(pathNames).contains("/api/v1/trades", "/api/v1/trades/previews", "/api/v1/trades/{activityId}", "/api/v1/investing/positions",
                "/api/v1/investing/positions/{accountId}/{instrumentId}");
        assertThat(paths.keySet().stream().allMatch(path -> path.toString().startsWith("/api/v1/"))).isTrue();

        var schemas = document.<Map<?, ?>>read("$.components.schemas");
        var schemaNames = schemas.keySet().stream().map(Object::toString).toList();
        assertThat(schemaNames).contains("TradeCommitRequest", "TradePreviewRequest", "TradePreviewResponse", "TradeResponse", "TradeSummaryResponse",
                "SecurityPostingResponse", "PositionResponse", "PostingResponse", "ActivityResponse");
        assertThat(requiredProperties(schemas, "TradeCommitRequest"))
                .containsAll(List.of("clientRequestId", "accountId", "instrumentId", "side", "quantity", "unitPrice", "commissionAmount", "recordingMode",
                        "effectiveAt", "economicSequence", "confirmPolicyBreach", "expectedCashBalanceVersion", "expectedPositionVersion"));
        assertThat(requiredProperties(schemas, "TradePreviewRequest")).containsAll(List.of("accountId", "instrumentId", "side", "quantity", "unitPrice",
                "commissionAmount", "recordingMode", "effectiveAt", "economicSequence", "confirmPolicyBreach"));
        assertThat(properties(schemas, "ActivityResponse").keySet().stream().map(Object::toString).toList()).contains("securityPostings");
        assertThat(requiredProperties(schemas, "ActivityResponse")).contains("securityPostings");
        assertRequiredProperties(schemas, "TradePreviewResponse", "accountId", "instrumentId", "instrumentSymbol", "side", "quantity", "unitPrice",
                "commissionAmount", "grossAmount", "currency", "recordingMode", "effectiveAt", "economicSequence", "cashDelta", "cashBalanceBefore",
                "cashBalanceAfter", "policyDecision", "allowed", "quantityBefore", "quantityAfter", "remainingBasisBefore", "remainingBasisAfter",
                "realizedEconomicPnlBefore", "realizedEconomicPnlAfter", "calculationPolicy", "cashBalanceVersion", "positionVersion");

        var tradeResponseFields = new String[]{"id", "accountId", "accountName", "instrumentId", "instrumentSymbol", "instrumentName", "instrumentType",
                "currency", "side", "quantity", "unitPrice", "grossAmount", "commissionAmount", "cashDelta", "quantityDelta", "effectiveAt", "recordedAt",
                "economicSequence", "recordingMode", "policyDecision", "sourceKind", "calculationPolicy", "cashPostings", "securityPosting"};
        assertRequiredProperties(schemas, "TradeResponse", tradeResponseFields);
        assertRequiredProperties(schemas, "TradeSummaryResponse", tradeResponseFields);
        assertRequiredProperties(schemas, "SecurityPostingResponse", "accountId", "instrumentId", "currency", "quantityDelta", "role", "effectiveAt",
                "economicSequence");
        assertRequiredProperties(schemas, "PositionResponse", "accountId", "accountName", "instrumentId", "instrumentSymbol", "instrumentName",
                "instrumentType", "currency", "quantity", "remainingEconomicBasis", "cumulativeRealizedEconomicPnl", "calculationPolicy", "projectionStatus",
                "asOf", "inputWatermarkActivityId", "lastSuccessfulBuildAt", "version");
        assertRequiredProperties(schemas, "ActivityResponse", "id", "activityType", "recordingMode", "effectiveAt", "recordedAt", "policyDecision",
                "sourceKind", "postings", "securityPostings");
        assertRequiredProperties(schemas, "PostingResponse", "accountId", "pocketId", "currency", "amount", "role");

        var sliceSchemas = schemas.entrySet().stream().filter(entry -> entry.getKey().toString().startsWith("SliceResponse")).toList();
        assertThat(sliceSchemas).isNotEmpty();

        for (var entry : sliceSchemas) {
            assertThat(entry.getValue()).isInstanceOf(Map.class);
            var required = ((Map<?, ?>) entry.getValue()).get("required");
            assertThat(required).isInstanceOf(List.class);
            assertThat(((List<?>) required).containsAll(List.of("items", "page", "size", "hasNext"))).isTrue();
        }
    }

    private static List<String> requiredProperties(Map<?, ?> schemas, String schemaName) {
        return ((List<?>) ((Map<?, ?>) schemas.get(schemaName)).get("required")).stream().map(Object::toString).toList();
    }

    private static void assertRequiredProperties(Map<?, ?> schemas, String schemaName, String... expectedProperties) {
        assertThat(requiredProperties(schemas, schemaName)).containsExactlyInAnyOrder(expectedProperties);
    }

    private static Map<?, ?> properties(Map<?, ?> schemas, String schemaName) {
        return (Map<?, ?>) ((Map<?, ?>) schemas.get(schemaName)).get("properties");
    }
}
