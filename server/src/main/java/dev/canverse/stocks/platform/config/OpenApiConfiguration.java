package dev.canverse.stocks.platform.config;

import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

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
        Map<String, Schema> properties = schema.getProperties();
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
