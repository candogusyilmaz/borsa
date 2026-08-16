package dev.canverse.stocks.reference.application;

import dev.canverse.stocks.platform.error.ValidationErrors;
import dev.canverse.stocks.reference.application.model.InstrumentSearchCriteria;
import dev.canverse.stocks.reference.application.model.InstrumentSearchCursor;
import dev.canverse.stocks.reference.infrastructure.ReferenceCatalogReadRepository;
import dev.canverse.stocks.reference.web.response.InstrumentPageResponse;
import dev.canverse.stocks.reference.web.response.InstrumentSummaryResponse;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstrumentSearchService {

    public static final int DEFAULT_LIMIT = 25;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 100;
    private static final int MAX_QUERY_LENGTH = 64;

    private final ReferenceCatalogReadRepository readRepository;
    private final InstrumentSearchCursorCodec cursorCodec;

    @Transactional(readOnly = true)
    public InstrumentPageResponse search(UUID ownerUserAccountId, InstrumentSearchCriteria criteria) {
        Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        Objects.requireNonNull(criteria, "criteria");
        var query = criteria.query();
        var marketId = criteria.marketId();
        var type = criteria.type();
        var includeInactive = criteria.includeInactive();
        var limit = criteria.limit();
        var cursor = criteria.cursor();
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw ValidationErrors.invalidField(
                    "limit",
                    "error.fields.reference.invalid_value",
                    "The limit must be between " + MIN_LIMIT + " and " + MAX_LIMIT + ".");
        }
        var queryNormalized = normalizeQuery(query);
        var filterDigest = cursorCodec.filterDigest(queryNormalized, marketId, type, includeInactive);
        var decodedCursor = cursor == null ? null : cursorCodec.decode(cursor, filterDigest);
        var views = readRepository.searchInstruments(
                ownerUserAccountId, queryNormalized, marketId, type, includeInactive, decodedCursor, limit + 1);
        var hasMore = views.size() > limit;
        var visible = hasMore ? views.subList(0, limit) : views;
        var summaries = visible.stream().map(InstrumentSummaryResponse::from).toList();
        var nextCursor = hasMore
                ? cursorCodec.encode(new InstrumentSearchCursor(
                        filterDigest,
                        visible.getLast().row().symbolNormalized(),
                        visible.getLast().row().marketCodeNormalized(),
                        visible.getLast().row().id()))
                : null;
        return InstrumentPageResponse.from(summaries, nextCursor);
    }

    private static String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        var trimmed = query.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_QUERY_LENGTH) {
            throw ValidationErrors.invalidField(
                    "query",
                    "error.fields.reference.invalid_value",
                    "The query must contain 1 to " + MAX_QUERY_LENGTH + " characters after trimming.");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
