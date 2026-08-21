package dev.canverse.stocks.platform.application;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Encodes and strictly decodes the transport envelope used by stateless HTTP cursors. */
@Component
public class CursorTokenCodec {

    public String encode(String canonicalPayload) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(canonicalPayload.getBytes(StandardCharsets.UTF_8));
    }

    public String decode(String encoded) {
        if (encoded == null || encoded.isBlank() || !encoded.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid cursor encoding");
        }
        try {
            var bytes = Base64.getUrlDecoder().decode(encoded);
            if (!encoded.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))) {
                throw new IllegalArgumentException("Non-canonical cursor encoding");
            }
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid cursor encoding", exception);
        }
    }
}
