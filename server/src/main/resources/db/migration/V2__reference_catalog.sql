-- V2 Reference catalogue: stable offline identities and explicit calendar coverage.
-- DDL and startup reference facts are owned by Flyway. No provider, observation,
-- price, rate, snapshot, extension, function, trigger, or generated ID is used.

CREATE TABLE reference.country (
    code text NOT NULL,
    name text NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_reference_country PRIMARY KEY (code),
    CONSTRAINT ck_reference_country_code CHECK (code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_reference_country_name CHECK (
        char_length(name) BETWEEN 1 AND 160
        AND name = btrim(name)
    )
);

CREATE TABLE reference.currency (
    code text NOT NULL,
    name text NOT NULL,
    symbol text NOT NULL,
    minor_unit smallint NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_reference_currency PRIMARY KEY (code),
    CONSTRAINT ck_reference_currency_code CHECK (code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_reference_currency_name CHECK (
        char_length(name) BETWEEN 1 AND 160
        AND name = btrim(name)
    ),
    CONSTRAINT ck_reference_currency_symbol CHECK (
        char_length(btrim(symbol)) > 0
        AND symbol = btrim(symbol)
    ),
    CONSTRAINT ck_reference_currency_minor_unit CHECK (minor_unit BETWEEN 0 AND 18)
);

CREATE TABLE reference.market (
    id uuid NOT NULL,
    code text NOT NULL,
    code_normalized text NOT NULL,
    name text NOT NULL,
    market_type text NOT NULL,
    country_code text,
    time_zone text NOT NULL,
    active boolean NOT NULL DEFAULT true,
    source_kind text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_reference_market PRIMARY KEY (id),
    CONSTRAINT uq_reference_market_code_normalized UNIQUE (code_normalized),
    CONSTRAINT fk_reference_market_country FOREIGN KEY (country_code)
        REFERENCES reference.country (code)
        ON DELETE RESTRICT,
    CONSTRAINT ck_reference_market_code CHECK (
        code ~ '^[A-Z0-9][A-Z0-9._-]{0,31}$'
        AND code_normalized = code
    ),
    CONSTRAINT ck_reference_market_code_normalized CHECK (
        char_length(code_normalized) BETWEEN 1 AND 32
        AND code_normalized = btrim(code_normalized)
        AND code_normalized = upper(code_normalized)
    ),
    CONSTRAINT ck_reference_market_name CHECK (
        char_length(name) BETWEEN 1 AND 160
        AND name = btrim(name)
    ),
    CONSTRAINT ck_reference_market_type CHECK (char_length(btrim(market_type)) > 0 AND market_type = btrim(market_type)),
    CONSTRAINT ck_reference_market_time_zone CHECK (char_length(btrim(time_zone)) > 0 AND time_zone = btrim(time_zone)),
    CONSTRAINT ck_reference_market_source_kind CHECK (
        char_length(btrim(source_kind)) > 0
        AND source_kind = btrim(source_kind)
    )
);

CREATE TABLE reference.market_currency (
    market_id uuid NOT NULL,
    currency_code text NOT NULL,
    primary_quote boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_reference_market_currency PRIMARY KEY (market_id, currency_code),
    CONSTRAINT fk_reference_market_currency_market FOREIGN KEY (market_id)
        REFERENCES reference.market (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_reference_market_currency_currency FOREIGN KEY (currency_code)
        REFERENCES reference.currency (code)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uix_reference_market_currency_primary
    ON reference.market_currency (market_id)
    WHERE primary_quote;

CREATE TABLE reference.instrument (
    id uuid NOT NULL,
    owner_user_account_id uuid,
    market_id uuid NOT NULL,
    symbol text NOT NULL,
    symbol_normalized text NOT NULL,
    name text NOT NULL,
    name_normalized text NOT NULL,
    instrument_type text NOT NULL,
    quotation_currency_code text NOT NULL,
    valuation_method text NOT NULL,
    active boolean NOT NULL DEFAULT true,
    source_kind text NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_reference_instrument PRIMARY KEY (id),
    CONSTRAINT fk_reference_instrument_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_reference_instrument_market FOREIGN KEY (market_id)
        REFERENCES reference.market (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_reference_instrument_market_currency FOREIGN KEY (market_id, quotation_currency_code)
        REFERENCES reference.market_currency (market_id, currency_code)
        ON DELETE RESTRICT,
    CONSTRAINT ck_reference_instrument_symbol CHECK (
        char_length(symbol) BETWEEN 1 AND 32
        AND symbol = btrim(symbol)
        AND symbol ~ '^[A-Za-z0-9][A-Za-z0-9._:/+-]*$'
    ),
    CONSTRAINT ck_reference_instrument_symbol_normalized CHECK (
        char_length(symbol_normalized) BETWEEN 1 AND 32
        AND symbol_normalized = btrim(symbol_normalized)
        AND symbol_normalized = upper(symbol_normalized)
        AND symbol_normalized = upper(symbol)
    ),
    CONSTRAINT ck_reference_instrument_name CHECK (
        char_length(name) BETWEEN 1 AND 160
        AND name = btrim(name)
    ),
    CONSTRAINT ck_reference_instrument_name_normalized CHECK (
        char_length(name_normalized) BETWEEN 1 AND 160
        AND name_normalized = btrim(name_normalized)
    ),
    CONSTRAINT ck_reference_instrument_type CHECK (
        char_length(btrim(instrument_type)) > 0
        AND instrument_type = btrim(instrument_type)
    ),
    CONSTRAINT ck_reference_instrument_valuation_method CHECK (
        char_length(btrim(valuation_method)) > 0
        AND valuation_method = btrim(valuation_method)
    ),
    CONSTRAINT ck_reference_instrument_source_kind CHECK (
        char_length(btrim(source_kind)) > 0
        AND source_kind = btrim(source_kind)
    ),
    CONSTRAINT ck_reference_instrument_owner_source CHECK (
        (owner_user_account_id IS NULL OR source_kind = 'USER_ENTERED')
        AND (source_kind <> 'USER_ENTERED' OR owner_user_account_id IS NOT NULL)
        AND (source_kind <> 'REFERENCE_SEED' OR owner_user_account_id IS NULL)
    ),
    CONSTRAINT ck_reference_instrument_version_non_negative CHECK (version >= 0)
);

CREATE UNIQUE INDEX uix_reference_instrument_global_symbol
    ON reference.instrument (market_id, symbol_normalized text_pattern_ops)
    WHERE owner_user_account_id IS NULL;

CREATE UNIQUE INDEX uix_reference_instrument_owner_symbol
    ON reference.instrument (owner_user_account_id, market_id, symbol_normalized text_pattern_ops)
    WHERE owner_user_account_id IS NOT NULL;

CREATE INDEX ix_reference_instrument_global_visibility
    ON reference.instrument (active, symbol_normalized text_pattern_ops, market_id, id)
    WHERE owner_user_account_id IS NULL;

CREATE INDEX ix_reference_instrument_owner_visibility
    ON reference.instrument (owner_user_account_id, active, symbol_normalized text_pattern_ops, market_id, id)
    WHERE owner_user_account_id IS NOT NULL;

CREATE INDEX ix_reference_instrument_name_prefix
    ON reference.instrument (name_normalized text_pattern_ops);

CREATE INDEX ix_reference_instrument_market_type
    ON reference.instrument (market_id, instrument_type, active, symbol_normalized text_pattern_ops, id);

CREATE TABLE reference.instrument_alias (
    id uuid NOT NULL,
    instrument_id uuid NOT NULL,
    alias_type text NOT NULL,
    alias_value text NOT NULL,
    alias_normalized text NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_reference_instrument_alias PRIMARY KEY (id),
    CONSTRAINT fk_reference_instrument_alias_instrument FOREIGN KEY (instrument_id)
        REFERENCES reference.instrument (id)
        ON DELETE CASCADE,
    CONSTRAINT uix_reference_instrument_alias_identity UNIQUE (instrument_id, alias_type, alias_normalized),
    CONSTRAINT ck_reference_instrument_alias_type CHECK (
        char_length(btrim(alias_type)) > 0
        AND alias_type = btrim(alias_type)
    ),
    CONSTRAINT ck_reference_instrument_alias_value CHECK (
        char_length(alias_value) BETWEEN 1 AND 128
        AND alias_value = btrim(alias_value)
    ),
    CONSTRAINT ck_reference_instrument_alias_normalized CHECK (
        char_length(alias_normalized) BETWEEN 1 AND 128
        AND alias_normalized = btrim(alias_normalized)
        AND alias_normalized = upper(alias_normalized)
    )
);

CREATE INDEX ix_reference_instrument_alias_exact
    ON reference.instrument_alias (alias_normalized, instrument_id);

CREATE INDEX ix_reference_instrument_alias_prefix
    ON reference.instrument_alias (alias_normalized text_pattern_ops, instrument_id);

CREATE TABLE reference.market_calendar (
    market_id uuid NOT NULL,
    calendar_date date NOT NULL,
    session_status text NOT NULL,
    opens_at time,
    closes_at time,
    source_kind text NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_reference_market_calendar PRIMARY KEY (market_id, calendar_date),
    CONSTRAINT fk_reference_market_calendar_market FOREIGN KEY (market_id)
        REFERENCES reference.market (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_reference_market_calendar_status CHECK (session_status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_reference_market_calendar_time_shape CHECK (
        (session_status = 'OPEN' AND opens_at IS NOT NULL AND closes_at IS NOT NULL AND closes_at > opens_at)
        OR (session_status = 'CLOSED' AND opens_at IS NULL AND closes_at IS NULL)
    ),
    CONSTRAINT ck_reference_market_calendar_source_kind CHECK (
        char_length(btrim(source_kind)) > 0
        AND source_kind = btrim(source_kind)
    )
);

COMMENT ON TABLE reference.country IS 'Stable ISO-like country identities used by the reference catalogue.';
COMMENT ON TABLE reference.currency IS 'Stable currency identities and display metadata; no exchange rates.';
COMMENT ON TABLE reference.market IS 'Market identities and timezone metadata; no trading-hours inference.';
COMMENT ON TABLE reference.market_currency IS 'Explicit market quotation-currency support relationships.';
COMMENT ON TABLE reference.instrument IS 'Global or owner-entered instrument identities without observations or positions.';
COMMENT ON TABLE reference.instrument_alias IS 'Search aliases for canonical instrument identities.';
COMMENT ON TABLE reference.market_calendar IS 'Explicit known local market-date coverage; missing rows remain unknown.';

INSERT INTO reference.country (code, name, created_at)
VALUES
    ('GB', 'United Kingdom', CURRENT_TIMESTAMP),
    ('TR', 'Türkiye', CURRENT_TIMESTAMP),
    ('US', 'United States', CURRENT_TIMESTAMP);

INSERT INTO reference.currency (code, name, symbol, minor_unit, created_at)
VALUES
    ('EUR', 'Euro', '€', 2, CURRENT_TIMESTAMP),
    ('GBP', 'Pound sterling', '£', 2, CURRENT_TIMESTAMP),
    ('TRY', 'Turkish lira', '₺', 2, CURRENT_TIMESTAMP),
    ('USD', 'United States dollar', '$', 2, CURRENT_TIMESTAMP);

INSERT INTO reference.market (
    id,
    code,
    code_normalized,
    name,
    market_type,
    country_code,
    time_zone,
    source_kind,
    created_at,
    updated_at
)
VALUES
    (
        '10000000-0000-0000-0000-000000000001',
        'XIST',
        'XIST',
        'Borsa Istanbul',
        'EXCHANGE',
        'TR',
        'Europe/Istanbul',
        'REFERENCE_SEED',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        'MANUAL',
        'MANUAL',
        'Manual or unlisted market',
        'MANUAL',
        NULL,
        'UTC',
        'REFERENCE_SEED',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

INSERT INTO reference.market_currency (market_id, currency_code, primary_quote)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'TRY', true),
    ('10000000-0000-0000-0000-000000000002', 'EUR', false),
    ('10000000-0000-0000-0000-000000000002', 'GBP', false),
    ('10000000-0000-0000-0000-000000000002', 'TRY', true),
    ('10000000-0000-0000-0000-000000000002', 'USD', false);
