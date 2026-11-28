package dev.canverse.stocks.domain.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@Converter
public class ListStringSpaceNormalizer implements AttributeConverter<List<String>, List<String>> {
    @Override
    public List<String> convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null) {
            return null;
        }

        return attribute.stream().map(StringUtils::normalizeSpace).toList();
    }

    @Override
    public List<String> convertToEntityAttribute(List<String> dbData) {
        return dbData;
    }
}
