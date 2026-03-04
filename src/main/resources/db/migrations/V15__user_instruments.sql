-- Add user_id to instruments table (nullable: NULL = system instrument, NOT NULL = user-created)
ALTER TABLE instrument.instruments
    ADD COLUMN user_id BIGINT;

ALTER TABLE instrument.instruments
    ADD CONSTRAINT fk_instruments_on_user FOREIGN KEY (user_id) REFERENCES account.users (id);

CREATE INDEX idx_instruments_user ON instrument.instruments (user_id);

-- Make market_id nullable for user-created instruments that may not belong to a market
ALTER TABLE instrument.instruments
    ALTER COLUMN market_id DROP NOT NULL;

-- Drop old unique constraint and replace with partial unique indexes
ALTER TABLE instrument.instruments
    DROP CONSTRAINT uc_instruments_symbol;

-- System instruments: unique on (symbol, market_id) where user_id IS NULL
CREATE UNIQUE INDEX uq_instruments_symbol_market
    ON instrument.instruments (symbol, market_id)
    WHERE user_id IS NULL;

-- User instruments: unique on (symbol, user_id) where user_id IS NOT NULL
CREATE UNIQUE INDEX uq_instruments_symbol_user
    ON instrument.instruments (symbol, user_id)
    WHERE user_id IS NOT NULL;

-- Custom instruments join table for user-created instruments
CREATE TABLE instrument.custom_instruments
(
    instrument_id BIGINT      NOT NULL,
    currency_code VARCHAR(3)  NOT NULL,
    CONSTRAINT pk_custom_instruments PRIMARY KEY (instrument_id),
    CONSTRAINT fk_custom_instruments_on_instrument FOREIGN KEY (instrument_id) REFERENCES instrument.instruments (id)
);
