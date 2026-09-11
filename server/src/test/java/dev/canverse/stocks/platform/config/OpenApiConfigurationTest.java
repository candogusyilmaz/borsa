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

    private static Schema<?> objectSchema(String... properties) {
        var schema = new ObjectSchema();
        for (String property : properties) {
            schema.addProperty(property, new Schema<>());
        }
        return schema;
    }
}
