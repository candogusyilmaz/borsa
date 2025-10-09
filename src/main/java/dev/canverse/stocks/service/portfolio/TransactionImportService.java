package dev.canverse.stocks.service.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.JsonSerializable;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.instrument.QInstrument;
import dev.canverse.stocks.domain.entity.portfolio.Transaction;
import dev.canverse.stocks.service.portfolio.model.BulkTransactionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionImportService {
    private final Client gemini;
    private final ObjectMapper mapper;
    private final JPAQueryFactory queryFactory;

    private final static Part PROMP_TEMPLATE = Part.fromText("""
            You are given the text content of a PDF that may include various financial information. Your task is to extract only the valid stock trade transactions — excluding any unrelated sections or cancelled trades.
            
            Return the result as a JSON array where each object has the following structure:
            
            {
            "date": "YYYY-MM-DD'T'hh:mm:ss.SSSZ",
            "symbol": "string",
            "currency": "string (optional)",
            "quantity": number (greater than 0),
            "avgPrice": number (big decimal),
            "type": "BUY or SELL",
            "marketCode": "BIST, NYSE, NASDAQ (optional)"
            }
            
            Instructions:
            
            Extract only executed and non-cancelled trades. Ignore cancelled, pending, or preview transactions.
            
            The type field must be either "BUY" or "SELL".
            
            Only include rows or text lines that represent actual trades. Ignore headers, summaries, portfolio holdings, or other sections.
            
            Dates should be normalized to YYYY-MM-DD'T'hh:mm:ss.SSSZ format if possible.
            
            Quantities must be positive numbers only.
            
            If the currency is not explicitly stated, omit the currency field.
            
            Do not include duplicate or partial data.
            
            The final output must be strictly valid JSON — no extra commentary or text.
            
            Example Output:
            
            [
            {
            "date": "2025-10-06T19:43:21.608Z",
            "symbol": "AKBNK",
            "currency": "TRY",
            "quantity": 100,
            "avgPrice": 29.45,
            "type": "BUY",
            "marketCode": "BIST"
            },
            {
            "date": "2025-10-06T19:43:21.608Z",
            "symbol": "THYAO",
            "currency": "TRY",
            "quantity": 200,
            "avgPrice": 347.8,
            "type": "SELL",
            "marketCode": "BIST"
            }
            ]
            """);

    private final static String JSON_SCHEMA = """
            {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "date": {
                    "type": "string",
                    "pattern": "\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}:\\\\d{2}\\\\.\\\\d{3}Z"
                  },
                  "symbol": {
                    "type": "string"
                  },
                  "currency": {
                    "type": "string"
                  },
                  "quantity": {
                    "type": "number",
                    "minimum": 0
                  },
                  "avgPrice": {
                    "type": "number"
                  },
                  "type": {
                    "type": "string",
                    "enum": ["BUY", "SELL"]
                  },
                  "marketCode": {
                    "type": "string",
                    "enum": ["BIST", "NYSE", "NASDAQ"]
                  }
                },
                "required": ["date", "symbol", "quantity", "avgPrice", "type"]
              }
            }
            """;

    public List<TransactionImportPreview> importTransactions(MultipartFile file) {
        try {
            var content = Content.fromParts(PROMP_TEMPLATE,
                    Part.fromBytes(file.getBytes(), file.getContentType())
            );

            var config = GenerateContentConfig.builder()
                    .temperature(0f)
                    .responseMimeType("application/json")
                    .responseJsonSchema(JsonSerializable.stringToJsonNode(JSON_SCHEMA))
                    .build();

            var response = gemini.models.generateContent("gemini-2.5-flash-lite", content, config);

            return mapper.readValue(response.text(), mapper.getTypeFactory().constructCollectionType(List.class, TransactionImportPreview.class));
        } catch (Exception ex) {
            log.error("Failed to import transactions", ex);
            throw new RuntimeException("Failed to import transactions", ex);
        }
    }

    public record TransactionImportPreview(
            Instant date,
            String symbol,
            String marketCode,
            String currency,
            BigDecimal quantity,
            BigDecimal avgPrice,
            Transaction.Type type
    ) {
    }

    public List<BulkTransactionRequest> parseImportedTransactions(List<TransactionImportPreview> imports) {
        var result = new ArrayList<BulkTransactionRequest>();

        var ins = QInstrument.instrument;

        for (var item : imports) {
            var instrument = queryFactory.selectFrom(ins)
                    .where(ins.symbol.eq(item.symbol).and(ins.market.code.eq(item.marketCode)))
                    .fetchFirst();

            if (instrument == null) {
                log.warn("No instrument found for symbol: {}", item.symbol);
                continue;
            }

            result.add(new BulkTransactionRequest(
                    item.type(),
                    instrument.getId(),
                    item.quantity(),
                    item.avgPrice(),
                    BigDecimal.ZERO,
                    item.date()
            ));
        }

        return result;
    }
}
