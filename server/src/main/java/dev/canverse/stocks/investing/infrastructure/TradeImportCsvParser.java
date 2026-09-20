package dev.canverse.stocks.investing.infrastructure;

import dev.canverse.stocks.investing.application.model.ParsedTradeImportFile;
import dev.canverse.stocks.investing.application.model.ParsedTradeImportRecord;
import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.platform.error.AppException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class TradeImportCsvParser {

    public static final int MAX_FILE_BYTES = 1_048_576;
    public static final int MAX_DATA_ROWS = 500;
    private static final byte[] UTF8_BOM = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};
    private static final List<String> HEADER = List.of("external_id", "side", "instrument_id", "currency", "effective_at", "economic_sequence", "quantity",
            "unit_price", "commission_amount");
    private static final CSVFormat HEADER_FORMAT = CSVFormat.RFC4180.builder().setAllowMissingColumnNames(false).setIgnoreEmptyLines(false)
            .setIgnoreSurroundingSpaces(false).setLenientEof(false).setTrailingData(false).setTrailingDelimiter(false).get();
    private static final CSVFormat DATA_FORMAT = CSVFormat.RFC4180.builder().setAllowMissingColumnNames(false).setIgnoreEmptyLines(false)
            .setIgnoreSurroundingSpaces(true).setLenientEof(false).setTrailingData(false).setTrailingDelimiter(false).get();

    public ParsedTradeImportFile parse(byte[] bytes, String submittedFileName, String submittedMediaType) {
        if (bytes.length == 0) {
            throw new AppException(InvestingErrorCode.IMPORT_FILE_EMPTY);
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new AppException(dev.canverse.stocks.platform.error.CommonErrorCode.PAYLOAD_TOO_LARGE);
        }
        var fileName = sanitizeFileName(submittedFileName);
        var mediaType = normalizeMediaType(submittedMediaType);
        var contentHash = sha256(bytes);
        var content = decode(bytes);
        if (content.isEmpty()) {
            throw new AppException(InvestingErrorCode.IMPORT_FILE_EMPTY);
        }

        try {
            var firstLineFeed = content.indexOf('\n');
            var headerText = firstLineFeed < 0 ? content : content.substring(0, firstLineFeed);
            if (firstLineFeed >= 0 && headerText.endsWith("\r")) {
                headerText = headerText.substring(0, headerText.length() - 1);
            }
            validateHeader(headerText);
            var dataText = firstLineFeed < 0 ? "" : content.substring(firstLineFeed + 1);
            var rows = new ArrayList<ParsedTradeImportRecord>();
            try (var parser = CSVParser.parse(new StringReader(dataText), DATA_FORMAT)) {
                for (CSVRecord record : parser) {
                    if (rows.size() == MAX_DATA_ROWS) {
                        throw new AppException(InvestingErrorCode.IMPORT_ROW_LIMIT_EXCEEDED);
                    }
                    var rowValues = values(record);
                    if (rowValues.stream().anyMatch(value -> value.indexOf('\0') >= 0)) {
                        throw new AppException(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID);
                    }
                    rows.add(new ParsedTradeImportRecord(Math.toIntExact(record.getRecordNumber()), rowValues));
                }
            }
            if (rows.isEmpty()) {
                throw new AppException(InvestingErrorCode.IMPORT_FILE_EMPTY);
            }
            return new ParsedTradeImportFile(fileName, mediaType, bytes.length, contentHash, rows);
        } catch (AppException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new AppException(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID, exception);
        }
    }

    private static void validateHeader(String headerText) throws IOException {
        try (var parser = CSVParser.parse(new StringReader(headerText), HEADER_FORMAT)) {
            var records = parser.iterator();
            if (!records.hasNext()) {
                throw new AppException(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID);
            }
            var headerRecord = records.next();
            var header = new ArrayList<String>(headerRecord.size());
            headerRecord.forEach(header::add);
            if (!HEADER.equals(header) || records.hasNext()) {
                throw new AppException(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID);
            }
        }
    }

    private static List<String> values(CSVRecord record) {
        var values = new ArrayList<String>(record.size());
        for (var value : record) {
            values.add(value.trim());
        }
        return List.copyOf(values);
    }

    private static String decode(byte[] bytes) {
        var offset = startsWithBom(bytes) ? UTF8_BOM.length : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (CharacterCodingException exception) {
            throw new AppException(InvestingErrorCode.IMPORT_FILE_ENCODING_INVALID, exception);
        }
    }

    private static boolean startsWithBom(byte[] bytes) {
        if (bytes.length < UTF8_BOM.length) {
            return false;
        }
        for (var index = 0; index < UTF8_BOM.length; index++) {
            if (bytes[index] != UTF8_BOM[index]) {
                return false;
            }
        }
        return true;
    }

    private static String sanitizeFileName(String submittedFileName) {
        if (submittedFileName == null) {
            throw new AppException(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID);
        }
        var normalized = submittedFileName.replace('\\', '/');
        var fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        var characterCount = fileName.codePointCount(0, fileName.length());
        if (characterCount < 1 || characterCount > 255 || fileName.indexOf('\0') >= 0) {
            throw new AppException(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID);
        }
        return fileName;
    }

    private static String normalizeMediaType(String submittedMediaType) {
        var mediaType = submittedMediaType == null ? "" : submittedMediaType.trim().toLowerCase(Locale.ROOT);
        if (mediaType.isEmpty()) {
            return "application/octet-stream";
        }
        if (mediaType.codePointCount(0, mediaType.length()) > 120 || mediaType.indexOf('\0') >= 0) {
            throw new AppException(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID);
        }
        return mediaType;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }
}
