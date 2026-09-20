package dev.canverse.stocks.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.canverse.stocks.testing.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
@IntegrationTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
class OpenApiHttpTest {
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
                "/api/v1/investing/positions/{accountId}/{instrumentId}", "/api/v1/portfolios", "/api/v1/portfolios/{portfolioId}",
                "/api/v1/portfolios/{portfolioId}/archive", "/api/v1/imports", "/api/v1/imports/{batchId}/preview", "/api/v1/imports/{batchId}/commit");
        assertThat(paths.keySet().stream().allMatch(path -> path.toString().startsWith("/api/v1/"))).isTrue();

        var schemas = document.<Map<?, ?>>read("$.components.schemas");
        var schemaNames = schemas.keySet().stream().map(Object::toString).toList();
        assertThat(schemaNames).contains("TradeCommitRequest", "TradePreviewRequest", "TradePreviewResponse", "TradeResponse", "TradeSummaryResponse",
                "SecurityPostingResponse", "PositionResponse", "PostingResponse", "ActivityResponse", "CreatePortfolioRequest", "UpdatePortfolioRequest",
                "ArchivePortfolioRequest", "PortfolioSummaryResponse", "PortfolioResponse", "PortfolioAccountResponse", "TradeImportCommitRequest",
                "TradeImportUploadResponse", "TradeImportPreviewResponse", "TradeImportCommitResponse", "TradeImportBatchIssueResponse",
                "TradeImportSummaryResponse", "TradeImportRowResponse", "TradeImportRowIssueResponse", "TradeImportPositionImpactResponse",
                "TradeImportPositionCommitResponse", "TradeImportUploadRequest");
        assertRequiredProperties(schemas, "CreatePortfolioRequest", "name", "accountIds");
        assertRequiredProperties(schemas, "UpdatePortfolioRequest", "name", "accountIds", "version");
        assertRequiredProperties(schemas, "ArchivePortfolioRequest", "version");
        assertRequiredProperties(schemas, "PortfolioSummaryResponse", "id", "name", "accountCount", "archived", "version", "createdAt", "updatedAt");
        assertRequiredProperties(schemas, "PortfolioResponse", "id", "name", "accountCount", "archived", "version", "createdAt", "updatedAt", "accounts");
        assertRequiredProperties(schemas, "PortfolioAccountResponse", "id", "name", "kind", "trackingMode", "currency", "archived");
        assertThat(((Map<?, ?>) properties(schemas, "PortfolioSummaryResponse").get("archivedAt")).get("nullable")).isEqualTo(true);
        assertThat(((Map<?, ?>) properties(schemas, "PortfolioResponse").get("archivedAt")).get("nullable")).isEqualTo(true);
        assertThat(((Map<?, ?>) properties(schemas, "PortfolioAccountResponse").get("archivedAt")).get("nullable")).isEqualTo(true);
        assertThat(requiredProperties(schemas, "PortfolioSummaryResponse")).doesNotContain("archivedAt");
        assertThat(requiredProperties(schemas, "PortfolioResponse")).doesNotContain("archivedAt");
        assertThat(requiredProperties(schemas, "PortfolioAccountResponse")).doesNotContain("archivedAt");
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
        assertRequiredProperties(schemas, "TradeImportCommitRequest", "clientRequestId", "previewToken");
        assertThat(((Map<?, ?>) properties(schemas, "TradeImportCommitRequest").get("previewToken")).get("pattern")).isEqualTo("[0-9a-f]{64}");
        assertRequiredProperties(schemas, "TradeImportUploadResponse", "id", "accountId", "importFormat", "status", "duplicateContent", "createdAt",
                "previewUrl");
        assertRequiredProperties(schemas, "TradeImportPreviewResponse", "id", "accountId", "importFormat", "status", "originalFileName", "mediaType",
                "byteSize", "contentSha256", "rowCount", "createdAt", "commitEligible", "batchIssues", "summary", "rows", "positionImpacts");
        assertThat(((Map<?, ?>) properties(schemas, "TradeImportPreviewResponse").get("committedAt")).get("nullable")).isEqualTo(true);
        assertThat(((Map<?, ?>) properties(schemas, "TradeImportPreviewResponse").get("previewToken")).get("nullable")).isEqualTo(true);
        assertThat(requiredProperties(schemas, "TradeImportPreviewResponse")).doesNotContain("committedAt", "previewToken");
        assertRequiredProperties(schemas, "TradeImportSummaryResponse", "rowCount", "normalizedRowCount", "issueRowCount", "duplicateRowCount", "buyCount",
                "sellCount", "currency", "buyGross", "sellGross", "commissionTotal", "cashDeltaTotal");
        for (var nullableField : List.of("cashBalanceBefore", "cashBalanceAfter", "accountVersion", "cashBalanceVersion")) {
            assertThat(((Map<?, ?>) properties(schemas, "TradeImportSummaryResponse").get(nullableField)).get("nullable")).isEqualTo(true);
            assertThat(requiredProperties(schemas, "TradeImportSummaryResponse")).doesNotContain(nullableField);
        }
        assertRequiredProperties(schemas, "TradeImportRowResponse", "id", "sourceRecordNumber", "sourceValues", "issues");
        for (var nullableField : List.of("externalId", "side", "instrumentId", "currency", "effectiveAt", "economicSequence", "quantity", "unitPrice",
                "commissionAmount", "grossAmount", "cashDelta", "rowFingerprint", "policyDecision", "committedActivityId")) {
            assertThat(((Map<?, ?>) properties(schemas, "TradeImportRowResponse").get(nullableField)).get("nullable")).isEqualTo(true);
            assertThat(requiredProperties(schemas, "TradeImportRowResponse")).doesNotContain(nullableField);
        }
        assertRequiredProperties(schemas, "TradeImportCommitResponse", "id", "accountId", "status", "committedRowCount", "activityIds", "cashBalanceAfter",
                "cashBalanceVersion", "positions", "committedAt");
        assertRequiredProperties(schemas, "TradeImportPositionImpactResponse", "instrumentId", "symbol", "currency", "positionVersion", "quantityBefore",
                "quantityAfter", "remainingBasisBefore", "remainingBasisAfter", "realizedEconomicPnlBefore", "realizedEconomicPnlAfter");
        assertRequiredProperties(schemas, "TradeImportPositionCommitResponse", "instrumentId", "positionVersion");
        assertThat(((Map<?, ?>) properties(schemas, "TradeImportSummaryResponse").get("buyGross")).get("type")).isEqualTo("string");
        assertThat(((Map<?, ?>) properties(schemas, "TradeImportCommitResponse").get("cashBalanceAfter")).get("type")).isEqualTo("string");

        var uploadOperation = operation(paths, "/api/v1/imports", "post");
        var uploadResponses = (Map<?, ?>) uploadOperation.get("responses");
        assertThat(uploadResponses.keySet().stream().map(Object::toString).toList()).contains("200", "201");
        for (var responseCode : List.of("200", "201")) {
            var uploadResponse = (Map<?, ?>) uploadResponses.get(responseCode);
            var uploadHeaders = (Map<?, ?>) uploadResponse.get("headers");
            assertThat(uploadHeaders.keySet().stream().map(Object::toString).toList()).contains("Location");
        }
        var uploadRequestBody = (Map<?, ?>) uploadOperation.get("requestBody");
        var multipartContent = (Map<?, ?>) ((Map<?, ?>) uploadRequestBody.get("content")).get("multipart/form-data");
        var multipartSchemaReference = (String) ((Map<?, ?>) multipartContent.get("schema")).get("$ref");
        assertThat(multipartSchemaReference).endsWith("/TradeImportUploadRequest");
        var multipartSchema = (Map<?, ?>) schemas.get("TradeImportUploadRequest");
        var multipartProperties = (Map<?, ?>) multipartSchema.get("properties");
        assertThat(multipartProperties.keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder("clientRequestId", "accountId", "file");
        assertThat(((List<?>) multipartSchema.get("required")).stream().map(Object::toString).toList()).containsExactlyInAnyOrder("clientRequestId",
                "accountId", "file");
        assertThat(((Map<?, ?>) multipartProperties.get("file")).get("format")).isEqualTo("binary");

        var sliceSchemas = schemas.entrySet().stream().filter(entry -> entry.getKey().toString().startsWith("SliceResponse")).toList();
        assertThat(sliceSchemas).isNotEmpty();

        for (var entry : sliceSchemas) {
            assertThat(entry.getValue()).isInstanceOf(Map.class);
            var required = ((Map<?, ?>) entry.getValue()).get("required");
            assertThat(required).isInstanceOf(List.class);
            assertThat(((List<?>) required).containsAll(List.of("items", "page", "size", "hasNext"))).isTrue();
        }

        assertThat(parameterNames(paths, "/api/v1/trades", "get")).contains("portfolioId");
        assertThat(parameterNames(paths, "/api/v1/investing/positions", "get")).contains("portfolioId");
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

    private static Map<?, ?> operation(Map<?, ?> paths, String path, String method) {
        return (Map<?, ?>) ((Map<?, ?>) paths.get(path)).get(method);
    }

    private static List<String> parameterNames(Map<?, ?> paths, String path, String method) {
        var pathItem = (Map<?, ?>) paths.get(path);
        var operation = (Map<?, ?>) pathItem.get(method);
        return ((List<?>) operation.get("parameters")).stream().map(parameter -> ((Map<?, ?>) parameter).get("name").toString()).toList();
    }
}
