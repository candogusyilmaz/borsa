package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.investing.infrastructure.TradeImportCsvParser;
import dev.canverse.stocks.platform.error.AppException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class TradeImportParserTest {

    private static final String HEADER = "external_id,side,instrument_id,currency,effective_at,economic_sequence,quantity,unit_price,commission_amount";
    private final TradeImportCsvParser parser = new TradeImportCsvParser();

    @Test
    void parsesQuotedFieldsLogicalRecordsAndSurroundingSpaces() {
        var content = HEADER + "\r\n  \" id, with \"\"quotes\"\"\nand a newline \"  , BUY ,00000000-0000-4000-8000-000000000001, USD ," +
                "2026-09-01T10:00:00Z, 1 , 2 , 3 , 0.25 \r\n" + "second,SELL,00000000-0000-4000-8000-000000000002,USD,2026-09-01T10:01:00Z,2,1,4,0";

        var parsed = parser.parse(content.getBytes(StandardCharsets.UTF_8), "C:\\broker\\history.csv", " TEXT/CSV ");

        assertThat(parsed.originalFileName()).isEqualTo("history.csv");
        assertThat(parsed.mediaType()).isEqualTo("text/csv");
        assertThat(parsed.records()).hasSize(2);
        assertThat(parsed.records().getFirst().sourceRecordNumber()).isEqualTo(1);
        assertThat(parsed.records().getFirst().values().getFirst()).isEqualTo("id, with \"quotes\"\nand a newline");
        assertThat(parsed.records().getFirst().values().get(1)).isEqualTo("BUY");
        assertThat(parsed.records().getLast().sourceRecordNumber()).isEqualTo(2);
    }

    @Test
    void hashesTheOriginalBytesAndAllowsOneUtf8Bom() throws Exception {
        var csv = (HEADER + "\nfirst,BUY,00000000-0000-4000-8000-000000000001,USD,2026-09-01T10:00:00Z,1,1,1,0").getBytes(StandardCharsets.UTF_8);
        var bytes = new byte[csv.length + 3];
        bytes[0] = (byte) 0xef;
        bytes[1] = (byte) 0xbb;
        bytes[2] = (byte) 0xbf;
        System.arraycopy(csv, 0, bytes, 3, csv.length);

        var parsed = parser.parse(bytes, "trades.csv", null);

        assertThat(parsed.contentSha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(parsed.records()).hasSize(1);
        assertThat(parsed.mediaType()).isEqualTo("application/octet-stream");
        assertThat(parser.parse(csv, "trades.csv", null).contentSha256()).isNotEqualTo(parsed.contentSha256());
    }

    @Test
    void rejectsMalformedUtf8CsvSyntaxAndNonExactHeadersWithoutParserDetails() {
        var invalidUtf8 = new byte[]{(byte) 0xc3, (byte) 0x28};
        assertCode(InvestingErrorCode.IMPORT_FILE_ENCODING_INVALID, () -> parser.parse(invalidUtf8, "x.csv", null));
        assertCode(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID,
                () -> parser.parse((HEADER + "\n\"unfinished").getBytes(StandardCharsets.UTF_8), "x.csv", null));
        var nulRecord = HEADER + "\nrow" + Character.toString((char) 0) + "bad,BUY,00000000-0000-4000-8000-000000000001,USD,2026-09-01T10:00:00Z,1,1,1,0";
        assertCode(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID, () -> parser.parse(nulRecord.getBytes(StandardCharsets.UTF_8), "x.csv", null));
        assertCode(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID,
                () -> parser.parse((HEADER.replace("side", "Side") + "\nrow").getBytes(StandardCharsets.UTF_8), "x.csv", null));
        assertCode(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID, () -> parser.parse((" " + HEADER + "\nrow").getBytes(StandardCharsets.UTF_8), "x.csv", null));
        assertCode(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID,
                () -> parser.parse((HEADER.replace("side", "side,side") + "\nrow").getBytes(StandardCharsets.UTF_8), "x.csv", null));
        assertCode(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID,
                () -> parser.parse((HEADER.replace(",side,", ",currency,side,") + "\nrow").getBytes(StandardCharsets.UTF_8), "x.csv", null));
        assertCode(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID,
                () -> parser.parse((HEADER.substring(0, HEADER.lastIndexOf(',')) + "\nrow").getBytes(StandardCharsets.UTF_8), "x.csv", null));
    }

    @Test
    void preservesEmptyRecordsAndEnforcesFileAndRowLimits() {
        var withEmptyRecord = parser.parse(
                (HEADER + "\n\n" + "id,BUY,00000000-0000-4000-8000-000000000001,USD,2026-09-01T10:00:00Z,1,1,1,0").getBytes(StandardCharsets.UTF_8), "x.csv",
                null);
        assertThat(withEmptyRecord.records()).hasSize(2);
        assertThat(withEmptyRecord.records().getFirst().values()).containsExactly("");

        var tooManyRows = new StringBuilder(HEADER).append('\n');
        for (var index = 0; index <= TradeImportCsvParser.MAX_DATA_ROWS; index++) {
            tooManyRows.append("row").append(index).append(",BUY,00000000-0000-4000-8000-000000000001,USD,2026-09-01T10:00:00Z,").append(index)
                    .append(",1,1,0\n");
        }
        assertCode(InvestingErrorCode.IMPORT_ROW_LIMIT_EXCEEDED, () -> parser.parse(tooManyRows.toString().getBytes(StandardCharsets.UTF_8), "x.csv", null));
        var fiveHundredRows = new StringBuilder(HEADER).append('\n');
        for (var index = 0; index < TradeImportCsvParser.MAX_DATA_ROWS; index++) {
            fiveHundredRows.append("row").append(index).append(",BUY,00000000-0000-4000-8000-000000000001,USD,2026-09-01T10:00:00Z,").append(index)
                    .append(",1,1,0\n");
        }
        assertThat(parser.parse(fiveHundredRows.toString().getBytes(StandardCharsets.UTF_8), "x.csv", null).records()).hasSize(500);

        var prefix = HEADER + "\n";
        var suffix = ",BUY,00000000-0000-4000-8000-000000000001,USD,2026-09-01T10:00:00Z,1,1,1,0";
        var paddedRecord = "x".repeat(
                TradeImportCsvParser.MAX_FILE_BYTES - prefix.getBytes(StandardCharsets.UTF_8).length - suffix.getBytes(StandardCharsets.UTF_8).length) + suffix;
        var exactLimit = (prefix + paddedRecord).getBytes(StandardCharsets.UTF_8);
        assertThat(exactLimit).hasSize(TradeImportCsvParser.MAX_FILE_BYTES);
        assertThat(parser.parse(exactLimit, "x.csv", null).records()).hasSize(1);
        assertCode(InvestingErrorCode.IMPORT_FILE_EMPTY, () -> parser.parse(new byte[0], "x.csv", null));
        assertCode(InvestingErrorCode.IMPORT_FILE_EMPTY, () -> parser.parse(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf}, "x.csv", null));
        assertCode(dev.canverse.stocks.platform.error.CommonErrorCode.PAYLOAD_TOO_LARGE,
                () -> parser.parse(new byte[TradeImportCsvParser.MAX_FILE_BYTES + 1], "x.csv", null));
        assertCode(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID, () -> parser.parse((HEADER + "\nrow").getBytes(StandardCharsets.UTF_8), "folder/", null));
    }

    private static void assertCode(Object expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode()).isEqualTo(expected));
    }
}
