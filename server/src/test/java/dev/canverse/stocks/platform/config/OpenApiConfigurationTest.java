package dev.canverse.stocks.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenApiConfigurationTest {

    private final OpenApiConfiguration configuration = new OpenApiConfiguration();

    @Test
    void paginationPropertiesAreRequiredWithoutDiscardingExistingRequirements() {
        var pagedModel = objectSchema("existing", "content", "page");
        pagedModel.setRequired(List.of("existing"));
        var pageMetadata = objectSchema("size", "number", "totalElements", "totalPages");
        var sliceResponse = objectSchema("items", "page", "size", "hasNext");
        var unrelated = objectSchema("value");

        var openApi = new OpenAPI().components(new Components().addSchemas("PagedModelAccountResponse", pagedModel).addSchemas("PageMetadata", pageMetadata)
                .addSchemas("SliceResponseActivityResponse", sliceResponse).addSchemas("Unrelated", unrelated));

        configuration.requiredPaginationPropertiesCustomizer().customise(openApi);

        assertThat(pagedModel.getRequired()).containsExactlyInAnyOrder("existing", "content", "page");
        assertThat(pageMetadata.getRequired()).containsExactlyInAnyOrder("size", "number", "totalElements", "totalPages");
        assertThat(sliceResponse.getRequired()).containsExactlyInAnyOrder("items", "page", "size", "hasNext");
        assertThat(unrelated.getRequired()).isNull();
    }

    @Test
    void customizerAcceptsAnOpenApiDocumentWithoutComponents() {
        var openApi = new OpenAPI();

        configuration.requiredPaginationPropertiesCustomizer().customise(openApi);

        assertThat(openApi.getComponents()).isNull();
    }

    @Test
    void apiErrorResponsesCustomizerRegistersSchemasAndAddsStandardErrorResponses() {
        var openApi = new OpenAPI();
        var paths = new io.swagger.v3.oas.models.Paths();

        var getOperation = new io.swagger.v3.oas.models.Operation();
        var postOperation = new io.swagger.v3.oas.models.Operation().requestBody(new io.swagger.v3.oas.models.parameters.RequestBody());

        paths.addPathItem("/test", new io.swagger.v3.oas.models.PathItem().get(getOperation).post(postOperation));
        openApi.setPaths(paths);

        configuration.apiErrorResponsesCustomizer().customise(openApi);

        var schemas = openApi.getComponents().getSchemas();
        assertThat(schemas).containsKeys("ApiProblem", "ValidationProblem", "ValidationError");

        var getResponses = getOperation.getResponses();
        assertThat(getResponses).containsKeys("400", "500");
        assertThat(getResponses).doesNotContainKey("422");
        assertThat(getResponses.get("400").getContent().get("application/problem+json").getSchema().get$ref()).isEqualTo("#/components/schemas/ApiProblem");
        assertThat(getResponses.get("500").getContent().get("application/problem+json").getSchema().get$ref()).isEqualTo("#/components/schemas/ApiProblem");

        var postResponses = postOperation.getResponses();
        assertThat(postResponses).containsKeys("400", "500", "422");
        assertThat(postResponses.get("422").getContent().get("application/problem+json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/ValidationProblem");
    }

    @Test
    void apiErrorResponsesCustomizerAcceptsOpenApiWithoutPathsOrComponents() {
        var openApi = new OpenAPI();

        configuration.apiErrorResponsesCustomizer().customise(openApi);

        assertThat(openApi.getComponents()).isNotNull();
        assertThat(openApi.getComponents().getSchemas()).containsKeys("ApiProblem", "ValidationProblem", "ValidationError");
    }

    @Test
    void enumSchemaCustomizerClearsNullableOnEnumSchemas() {
        var enumSchema = new Schema<String>()._enum(List.of("BUY", "SELL"));
        enumSchema.setNullable(true);
        var regularSchema = new Schema<String>();
        regularSchema.setNullable(true);

        var openApi = new OpenAPI().components(new Components().addSchemas("TradeSide", enumSchema).addSchemas("Regular", regularSchema));

        configuration.enumSchemaCustomizer().customise(openApi);

        assertThat(enumSchema.getNullable()).isNull();
        assertThat(regularSchema.getNullable()).isTrue();
    }

    private static Schema<?> objectSchema(String... properties) {
        var schema = new ObjectSchema();
        for (String property : properties) {
            schema.addProperty(property, new Schema<>());
        }
        return schema;
    }
}
