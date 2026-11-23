-- 1. Add instrument_id column to positions
ALTER TABLE portfolio.positions
    ADD COLUMN instrument_id BIGINT;

-- 2. Update instrument_id for each position
UPDATE portfolio.positions p
SET instrument_id = si.id
FROM instrument.instruments si
         JOIN stocks s ON si.symbol = s.symbol
WHERE p.stock_id = s.id;

-- 3. Add foreign key constraint (optional, recommended)
ALTER TABLE portfolio.positions
    ADD CONSTRAINT fk_positions_instrument
        FOREIGN KEY (instrument_id) REFERENCES instrument.instruments (id);

-- 4. (Optional) Remove old stock_id column if migration is complete
ALTER TABLE portfolio.positions
    DROP COLUMN stock_id;

-- 5. Copy stock snapshots to instrument snapshots
INSERT INTO instrument.instrument_snapshots (instrument_id,
                                             last,
                                             daily_change,
                                             daily_change_percent,
                                             previous_close,
                                             updated_at)
SELECT si.instrument_id,
       ss.last,
       ss.daily_change,
       ss.daily_change_percent,
       ss.previous_close,
       ss.updated_at
FROM instrument.stock_instruments si
         JOIN instrument.instruments i ON si.instrument_id = i.id
         JOIN stocks s ON i.symbol = s.symbol
         JOIN stock_snapshots ss ON ss.stock_id = s.id;

-- 6. Drop stock related tables if no longer needed
DROP TABLE IF EXISTS stock_splits;
DROP TABLE IF EXISTS stock_snapshots;
DROP TABLE IF EXISTS stocks;
DROP TABLE IF EXISTS exchanges;
