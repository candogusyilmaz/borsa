CREATE TABLE ledger.trade_import_batch (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    financial_account_id uuid NOT NULL,
    import_format text NOT NULL,
    status text NOT NULL,
    original_file_name text NOT NULL,
    media_type text NOT NULL,
    byte_size bigint NOT NULL,
    content_sha256 text NOT NULL,
    parsed_row_count integer NOT NULL,
    created_at timestamptz NOT NULL,
    committed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_ledger_trade_import_batch PRIMARY KEY (id),
    CONSTRAINT fk_ledger_trade_import_batch_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_trade_import_batch_account FOREIGN KEY (owner_user_account_id, financial_account_id)
        REFERENCES ledger.financial_account (owner_user_account_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_ledger_trade_import_batch_owner_id UNIQUE (owner_user_account_id, id),
    CONSTRAINT uq_ledger_trade_import_batch_content UNIQUE (
        owner_user_account_id, financial_account_id, import_format, content_sha256
    ),
    CONSTRAINT ck_ledger_trade_import_batch_format CHECK (import_format = 'FUNDED_TRADE_CSV_V1'),
    CONSTRAINT ck_ledger_trade_import_batch_status CHECK (status IN ('PARSED', 'COMMITTED')),
    CONSTRAINT ck_ledger_trade_import_batch_file_name CHECK (
        char_length(original_file_name) BETWEEN 1 AND 255 AND original_file_name = btrim(original_file_name)
    ),
    CONSTRAINT ck_ledger_trade_import_batch_media_type CHECK (
        char_length(media_type) BETWEEN 1 AND 120 AND media_type = btrim(media_type)
    ),
    CONSTRAINT ck_ledger_trade_import_batch_byte_size CHECK (byte_size BETWEEN 1 AND 1048576),
    CONSTRAINT ck_ledger_trade_import_batch_sha256 CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ledger_trade_import_batch_row_count CHECK (parsed_row_count BETWEEN 1 AND 500),
    CONSTRAINT ck_ledger_trade_import_batch_status_shape CHECK (
        (status = 'PARSED' AND committed_at IS NULL)
        OR (status = 'COMMITTED' AND committed_at IS NOT NULL AND committed_at >= created_at)
    ),
    CONSTRAINT ck_ledger_trade_import_batch_version_non_negative CHECK (version >= 0)
);

CREATE TABLE ledger.trade_import_row (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    import_batch_id uuid NOT NULL,
    source_record_number integer NOT NULL,
    source_external_id text,
    source_values jsonb NOT NULL,
    normalization_status text NOT NULL,
    side text,
    instrument_id uuid,
    currency_code text,
    effective_at timestamptz,
    economic_sequence bigint,
    quantity numeric(38, 18),
    unit_price numeric(38, 18),
    commission_amount numeric(38, 18),
    gross_amount numeric(38, 18),
    cash_delta numeric(38, 18),
    row_fingerprint text,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_ledger_trade_import_row PRIMARY KEY (id),
    CONSTRAINT fk_ledger_trade_import_row_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_trade_import_row_batch FOREIGN KEY (owner_user_account_id, import_batch_id)
        REFERENCES ledger.trade_import_batch (owner_user_account_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_trade_import_row_instrument FOREIGN KEY (instrument_id)
        REFERENCES reference.instrument (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_trade_import_row_currency FOREIGN KEY (currency_code)
        REFERENCES reference.currency (code) ON DELETE RESTRICT,
    CONSTRAINT uq_ledger_trade_import_row_owner_id UNIQUE (owner_user_account_id, id),
    CONSTRAINT uq_ledger_trade_import_row_owner_batch_id UNIQUE (owner_user_account_id, import_batch_id, id),
    CONSTRAINT uq_ledger_trade_import_row_record UNIQUE (owner_user_account_id, import_batch_id, source_record_number),
    CONSTRAINT ck_ledger_trade_import_row_record_number CHECK (source_record_number BETWEEN 1 AND 500),
    CONSTRAINT ck_ledger_trade_import_row_source_values CHECK (
        jsonb_typeof(source_values) = 'array' AND octet_length(source_values::text) <= 65536
    ),
    CONSTRAINT ck_ledger_trade_import_row_normalization_status CHECK (normalization_status IN ('VALID', 'INVALID')),
    CONSTRAINT ck_ledger_trade_import_row_external_id CHECK (
        source_external_id IS NULL OR (
            char_length(source_external_id) BETWEEN 1 AND 200 AND source_external_id = btrim(source_external_id)
        )
    ),
    CONSTRAINT ck_ledger_trade_import_row_fingerprint CHECK (
        row_fingerprint IS NULL OR row_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_ledger_trade_import_row_normalized_shape CHECK (
        (
            normalization_status = 'INVALID'
            AND side IS NULL AND instrument_id IS NULL AND currency_code IS NULL AND effective_at IS NULL
            AND economic_sequence IS NULL AND quantity IS NULL AND unit_price IS NULL AND commission_amount IS NULL
            AND gross_amount IS NULL AND cash_delta IS NULL AND row_fingerprint IS NULL
        )
        OR (
            normalization_status = 'VALID'
            AND side IS NOT NULL AND side IN ('BUY', 'SELL')
            AND instrument_id IS NOT NULL AND currency_code IS NOT NULL AND effective_at IS NOT NULL
            AND economic_sequence IS NOT NULL AND economic_sequence >= 0
            AND quantity IS NOT NULL AND quantity > 0 AND unit_price IS NOT NULL AND unit_price > 0
            AND commission_amount IS NOT NULL AND commission_amount >= 0
            AND gross_amount IS NOT NULL AND gross_amount > 0 AND cash_delta IS NOT NULL AND cash_delta <> 0
            AND row_fingerprint IS NOT NULL AND source_external_id IS NOT NULL
            AND ((side = 'BUY' AND cash_delta < 0) OR (side = 'SELL' AND cash_delta > 0))
        )
    )
);

CREATE INDEX ix_ledger_trade_import_row_owner_fingerprint
    ON ledger.trade_import_row (owner_user_account_id, row_fingerprint)
    WHERE row_fingerprint IS NOT NULL;

CREATE TABLE ledger.trade_import_issue (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    import_batch_id uuid NOT NULL,
    import_row_id uuid NOT NULL,
    issue_code text NOT NULL,
    field_name text NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_ledger_trade_import_issue PRIMARY KEY (id),
    CONSTRAINT fk_ledger_trade_import_issue_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_trade_import_issue_row FOREIGN KEY (owner_user_account_id, import_batch_id, import_row_id)
        REFERENCES ledger.trade_import_row (owner_user_account_id, import_batch_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_ledger_trade_import_issue_code_field UNIQUE (owner_user_account_id, import_row_id, issue_code, field_name),
    CONSTRAINT ck_ledger_trade_import_issue_field CHECK (
        char_length(field_name) BETWEEN 1 AND 80 AND field_name = btrim(field_name)
    ),
    CONSTRAINT ck_ledger_trade_import_issue_code CHECK (issue_code IN (
        'RECORD_SHAPE_INVALID', 'EXTERNAL_ID_INVALID', 'SIDE_INVALID', 'INSTRUMENT_ID_INVALID',
        'INSTRUMENT_UNSUPPORTED', 'CURRENCY_INVALID', 'CURRENCY_MISMATCH', 'EFFECTIVE_AT_INVALID',
        'ECONOMIC_SEQUENCE_INVALID', 'QUANTITY_INVALID', 'UNIT_PRICE_INVALID', 'COMMISSION_INVALID',
        'SETTLED_PRECISION_INVALID', 'SELL_PROCEEDS_NOT_POSITIVE', 'DUPLICATE_EXTERNAL_ID',
        'DUPLICATE_ROW_IN_FILE', 'ECONOMIC_ORDER_CONFLICT_IN_FILE'
    ))
);

ALTER TABLE ledger.activity
    ADD COLUMN source_import_row_id uuid;

ALTER TABLE ledger.activity
    DROP CONSTRAINT ck_ledger_activity_source_kind,
    ADD CONSTRAINT ck_ledger_activity_source_kind CHECK (source_kind IN ('USER_ENTERED', 'FILE_IMPORTED')),
    ADD CONSTRAINT fk_ledger_activity_source_import_row FOREIGN KEY (owner_user_account_id, source_import_row_id)
        REFERENCES ledger.trade_import_row (owner_user_account_id, id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT ck_ledger_activity_source_shape CHECK (
        (source_kind = 'USER_ENTERED' AND source_import_row_id IS NULL)
        OR (
            source_kind = 'FILE_IMPORTED' AND source_import_row_id IS NOT NULL
            AND activity_type IN ('SECURITY_BUY', 'SECURITY_SELL') AND recording_mode = 'HISTORICAL_FACT'
        )
    );

CREATE UNIQUE INDEX uq_ledger_activity_source_import_row
    ON ledger.activity (owner_user_account_id, source_import_row_id)
    WHERE source_import_row_id IS NOT NULL;
