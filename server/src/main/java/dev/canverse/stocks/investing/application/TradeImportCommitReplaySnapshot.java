package dev.canverse.stocks.investing.application;

import dev.canverse.stocks.investing.domain.TradeImportStatus;
import dev.canverse.stocks.investing.web.response.TradeImportCommitResponse;
import dev.canverse.stocks.investing.web.response.TradeImportPositionCommitResponse;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Compact, import-specific state needed to reproduce a committed response from its idempotency record. */
public record TradeImportCommitReplaySnapshot(
        UUID accountId,
        String cashBalanceAfter,
        long cashBalanceVersion,
        String packedActivityIds,
        String packedPositions,
        Instant committedAt
) {

    public static TradeImportCommitReplaySnapshot from(TradeImportCommitResponse response) {
        return new TradeImportCommitReplaySnapshot(response.accountId(), response.cashBalanceAfter(), response.cashBalanceVersion(),
                packActivityIds(response.activityIds()), packPositions(response.positions()), response.committedAt());
    }

    public TradeImportCommitResponse toResponse(UUID batchId) {
        var activityIds = unpackActivityIds(packedActivityIds);
        return new TradeImportCommitResponse(batchId, accountId, TradeImportStatus.COMMITTED, activityIds.size(), activityIds, cashBalanceAfter,
                cashBalanceVersion, unpackPositions(packedPositions), committedAt);
    }

    private static String packActivityIds(List<UUID> activityIds) {
        var buffer = ByteBuffer.allocate(Math.multiplyExact(activityIds.size(), 16));
        for (var activityId : activityIds) {
            buffer.putLong(activityId.getMostSignificantBits()).putLong(activityId.getLeastSignificantBits());
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    private static List<UUID> unpackActivityIds(String packed) {
        var bytes = decode(packed);
        if (bytes.length % 16 != 0) {
            throw new IllegalStateException("Stored trade import activity IDs are malformed");
        }
        var buffer = ByteBuffer.wrap(bytes);
        var result = new ArrayList<UUID>(bytes.length / 16);
        while (buffer.hasRemaining()) {
            result.add(new UUID(buffer.getLong(), buffer.getLong()));
        }
        return List.copyOf(result);
    }

    private static String packPositions(List<TradeImportPositionCommitResponse> positions) {
        var buffer = ByteBuffer.allocate(Math.multiplyExact(positions.size(), 24));
        for (var position : positions) {
            buffer.putLong(position.instrumentId().getMostSignificantBits()).putLong(position.instrumentId().getLeastSignificantBits())
                    .putLong(position.positionVersion());
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    private static List<TradeImportPositionCommitResponse> unpackPositions(String packed) {
        var bytes = decode(packed);
        if (bytes.length % 24 != 0) {
            throw new IllegalStateException("Stored trade import position versions are malformed");
        }
        var buffer = ByteBuffer.wrap(bytes);
        var result = new ArrayList<TradeImportPositionCommitResponse>(bytes.length / 24);
        while (buffer.hasRemaining()) {
            var instrumentId = new UUID(buffer.getLong(), buffer.getLong());
            var positionVersion = buffer.getLong();
            if (positionVersion < 0) {
                throw new IllegalStateException("Stored trade import position version is malformed");
            }
            result.add(new TradeImportPositionCommitResponse(instrumentId, positionVersion));
        }
        return List.copyOf(result);
    }

    private static byte[] decode(String packed) {
        try {
            return Base64.getUrlDecoder().decode(packed);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Stored trade import replay data is malformed", exception);
        }
    }
}
