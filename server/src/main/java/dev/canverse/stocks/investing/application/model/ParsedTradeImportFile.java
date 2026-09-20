package dev.canverse.stocks.investing.application.model;

import java.util.List;

public record ParsedTradeImportFile(String originalFileName, String mediaType, long byteSize, String contentSha256, List<ParsedTradeImportRecord> records) {

    public ParsedTradeImportFile {
        records = List.copyOf(records);
    }
}
