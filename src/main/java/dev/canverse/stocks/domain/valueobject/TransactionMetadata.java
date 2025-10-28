package dev.canverse.stocks.domain.valueobject;

import dev.canverse.stocks.domain.common.ListStringSpaceNormalizer;
import dev.canverse.stocks.domain.common.StringSpaceNormalizer;
import jakarta.persistence.Convert;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TransactionMetadata {
    @Convert(converter = ListStringSpaceNormalizer.class)
    private List<String> tags;

    @Convert(converter = StringSpaceNormalizer.class)
    private String notes;
}
