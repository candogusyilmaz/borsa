package dev.canverse.stocks.reference.application;

import dev.canverse.stocks.platform.error.ValidationErrors;
import dev.canverse.stocks.platform.web.SliceResponse;
import dev.canverse.stocks.reference.application.model.InstrumentSearchCriteria;
import dev.canverse.stocks.reference.infrastructure.ReferenceCatalogReadRepository;
import dev.canverse.stocks.reference.web.response.InstrumentSummaryResponse;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstrumentSearchService {

    private static final int MAX_QUERY_LENGTH = 64;

    private final ReferenceCatalogReadRepository readRepository;

    @Transactional(readOnly = true)
    public SliceResponse<InstrumentSummaryResponse> search(
            UUID ownerUserAccountId, InstrumentSearchCriteria criteria, Pageable pageable) {
        Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        Objects.requireNonNull(criteria, "criteria");
        var query = criteria.query();
        var marketId = criteria.marketId();
        var type = criteria.type();
        var includeInactive = criteria.includeInactive();
        var queryNormalized = normalizeQuery(query);
        return readRepository.searchInstruments(
                ownerUserAccountId, queryNormalized, marketId, type, includeInactive, pageable);
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
