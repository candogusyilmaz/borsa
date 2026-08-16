package dev.canverse.stocks.reference.application;

import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.reference.application.model.InstrumentSearchCursor;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.error.ReferenceErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class InstrumentSearchCursorCodec {

    private static final Pattern PAYLOAD =
            Pattern.compile("\\{\"v\":1,\"f\":\"([0-9a-f]{64})\",\"s\":\"([A-Z0-9][A-Z0-9._:/+\\-]{0,31})\","
                    + "\"m\":\"([A-Z0-9][A-Z0-9._-]{0,31})\","
                    + "\"i\":\"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\"}");

    public String encode(InstrumentSearchCursor cursor) {
        var payload = "{\"v\":1,\"f\":\"%s\",\"s\":\"%s\",\"m\":\"%s\",\"i\":\"%s\"}"
                .formatted(
                        cursor.filterDigest(),
                        cursor.symbolNormalized(),
                        cursor.marketCodeNormalized(),
                        cursor.instrumentId().toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public InstrumentSearchCursor decode(String encoded, String expectedFilterDigest) {
        try {
            if (encoded == null || encoded.isBlank() || !encoded.matches("[A-Za-z0-9_-]+")) {
                throw invalidCursor();
            }
            var bytes = Base64.getUrlDecoder().decode(encoded);
            var json = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            Matcher matcher = PAYLOAD.matcher(json);
            if (!matcher.matches()) {
                throw invalidCursor();
            }
            var canonical = matcher.group(0);
            if (!canonical.equals(json)
                    || !encoded.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))) {
                throw invalidCursor();
            }
            var suppliedDigest = matcher.group(1);
            if (!MessageDigest.isEqual(
                    expectedFilterDigest.getBytes(StandardCharsets.US_ASCII),
                    suppliedDigest.getBytes(StandardCharsets.US_ASCII))) {
                throw invalidCursor();
            }
            return new InstrumentSearchCursor(
                    suppliedDigest, matcher.group(2), matcher.group(3), UUID.fromString(matcher.group(4)));
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidCursor();
        } catch (CharacterCodingException exception) {
            throw invalidCursor();
        }
    }

    public String filterDigest(String queryNormalized, UUID marketId, InstrumentType type, boolean includeInactive) {
        var filter = "%s\n%s\n%s\n%s"
                .formatted(
                        queryNormalized == null ? "" : queryNormalized,
                        marketId == null ? "" : marketId.toString(),
                        type == null ? "" : type.name(),
                        includeInactive);
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(filter.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    private AppException invalidCursor() {
        return new AppException(ReferenceErrorCode.INVALID_INSTRUMENT_CURSOR);
    }
}
