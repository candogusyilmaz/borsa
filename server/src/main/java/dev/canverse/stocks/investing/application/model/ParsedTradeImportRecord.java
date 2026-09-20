package dev.canverse.stocks.investing.application.model;

import java.util.List;

public record ParsedTradeImportRecord(int sourceRecordNumber, List<String> values) {

    public ParsedTradeImportRecord {
        values = List.copyOf(values);
    }
}
