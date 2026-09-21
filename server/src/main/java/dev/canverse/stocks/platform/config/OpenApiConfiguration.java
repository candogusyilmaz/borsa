package dev.canverse.stocks.platform.config;

import dev.canverse.stocks.platform.error.ApiProblem;
import dev.canverse.stocks.platform.error.ValidationError;
import dev.canverse.stocks.platform.error.ValidationProblem;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String PROBLEM_JSON_MEDIA_TYPE = "application/problem+json";

    static {
        ModelResolver.enumsAsRef = true;
    }

    @Bean
    GlobalOpenApiCustomizer apiErrorResponsesCustomizer() {
        return openApi -> {
            var components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            if (components.getSchemas() == null) {
                components.setSchemas(new LinkedHashMap<>());
            }

            var converter = ModelConverters.getInstance();
            components.getSchemas().putAll(converter.readAll(ApiProblem.class));
            components.getSchemas().putAll(converter.readAll(ValidationProblem.class));
            components.getSchemas().putAll(converter.readAll(ValidationError.class));

            var problemMediaType = new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiProblem"));
            var validationMediaType = new MediaType().schema(new Schema<>().$ref("#/components/schemas/ValidationProblem"));

            var badRequestContent = new Content().addMediaType(PROBLEM_JSON_MEDIA_TYPE, problemMediaType);
            var internalErrorContent = new Content().addMediaType(PROBLEM_JSON_MEDIA_TYPE, problemMediaType);
            var validationContent = new Content().addMediaType(PROBLEM_JSON_MEDIA_TYPE, validationMediaType);

            var paths = openApi.getPaths();
            if (paths == null) {
                return;
            }

            for (var pathItem : paths.values()) {
                for (var operation : pathItem.readOperations()) {
                    var responses = operation.getResponses();
                    if (responses == null) {
                        responses = new ApiResponses();
                        operation.setResponses(responses);
                    }

                    if (!responses.containsKey("400")) {
                        responses.addApiResponse("400", new ApiResponse().description("Bad Request").content(badRequestContent));
                    }
                    if (!responses.containsKey("500")) {
                        responses.addApiResponse("500", new ApiResponse().description("Internal Server Error").content(internalErrorContent));
                    }
                    if (operation.getRequestBody() != null && !responses.containsKey("422")) {
                        responses.addApiResponse("422", new ApiResponse().description("Validation Failed").content(validationContent));
                    }
                }
            }
        };
    }

    @Bean
    GlobalOpenApiCustomizer enumSchemaCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            for (var schema : openApi.getComponents().getSchemas().values()) {
                if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
                    schema.setNullable(null);
                }
            }
        };
    }

    @Bean
    GlobalOpenApiCustomizer requiredPaginationPropertiesCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }

            openApi.getComponents().getSchemas().forEach((name, schema) -> markRequiredPaginationProperties(name, schema));
        };
    }

    private static void markRequiredPaginationProperties(String name, Schema<?> schema) {
        var properties = schema.getProperties();
        if (properties == null) {
            return;
        }

        if (name.startsWith("PagedModel") && properties.keySet().containsAll(Set.of("content", "page"))) {
            addRequired(schema, "content", "page");
        }

        if (name.equals("PageMetadata") && properties.keySet().containsAll(Set.of("size", "number", "totalElements", "totalPages"))) {
            addRequired(schema, "size", "number", "totalElements", "totalPages");
        }

        if (name.startsWith("SliceResponse") && properties.keySet().containsAll(Set.of("items", "page", "size", "hasNext"))) {
            addRequired(schema, "items", "page", "size", "hasNext");
        }
    }

    private static void addRequired(Schema<?> schema, String... propertyNames) {
        var required = new LinkedHashSet<String>();
        if (schema.getRequired() != null) {
            required.addAll(schema.getRequired());
        }

        for (String propertyName : propertyNames) {
            required.add(propertyName);
        }
        schema.setRequired(new ArrayList<>(required));
    }
}
