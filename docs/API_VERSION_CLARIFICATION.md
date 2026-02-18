# API Version Documentation

## Trade APIs

The application has two Trade controller endpoints that serve different purposes:

### TradeController (`/api/portfolios/{portfolioId}/trades`)
**Purpose**: Portfolio-specific trade management
**Status**: ✅ Active

**Endpoints**:
- `GET /api/portfolios/{portfolioId}/trades` - Fetch trade history for a specific portfolio
- `POST /api/portfolios/{portfolioId}/trades/buy` - Execute buy trade
- `POST /api/portfolios/{portfolioId}/trades/sell` - Execute sell trade
- `POST /api/portfolios/{portfolioId}/trades/bulk` - Bulk import trades
- `POST /api/portfolios/{portfolioId}/trades/undo/{holdingId}` - Undo latest trade
- `POST /api/portfolios/{portfolioId}/trades/import` - Import trades from file

**Use Case**: Managing trades within a specific portfolio context

### TradeControllerV2 (`/api/trades`)
**Purpose**: User-level trade viewing with filtering
**Status**: ✅ Active

**Endpoints**:
- `GET /api/trades` - Fetch all trades for the authenticated user with query filters

**Use Case**: Viewing and filtering trades across all portfolios for a user

## Conclusion

Both controllers serve valid, distinct purposes:
- **TradeController**: Portfolio-scoped operations (CRUD operations on trades)
- **TradeControllerV2**: User-scoped read operations (viewing/filtering across portfolios)

**Recommendation**: Keep both controllers as they serve different use cases. The "V2" naming is misleading - consider renaming to `UserTradesController` or similar in a future refactor, but this is a low-priority cosmetic change.

## Future Improvements

### Potential Naming Refactor (Low Priority)
```
TradeController → PortfolioTradeController
TradeControllerV2 → UserTradesController or TradesQueryController
```

This would clarify their distinct purposes without breaking existing API contracts.
