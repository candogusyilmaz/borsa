-- V3 Financial accounts and immutable native-currency cash ledger.
-- The six tables in this migration are the only financial-truth structures in
-- this implementation unit. Facts are append-only; projections are rebuildable.

CREATE TABLE ledger.financial_account (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    name text NOT NULL,
    name_normalized text NOT NULL,
    account_kind text NOT NULL,
    tracking_mode text NOT NULL,
    negative_balance_policy text,
    currency_code text NOT NULL,
    time_zone text NOT NULL,
    authorized_limit numeric(38, 18),
    current_opening_activity_id uuid,
    archived_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_ledger_financial_account PRIMARY KEY (id),
    CONSTRAINT fk_ledger_financial_account_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_financial_account_currency FOREIGN KEY (currency_code)
        REFERENCES reference.currency (code) ON DELETE RESTRICT,
    CONSTRAINT uq_ledger_financial_account_owner_id UNIQUE (owner_user_account_id, id),
    CONSTRAINT uq_ledger_financial_account_id_currency UNIQUE (id, currency_code),
    CONSTRAINT ck_ledger_financial_account_name CHECK (
        char_length(name) BETWEEN 1 AND 160
        AND name = btrim(name)
    ),
    CONSTRAINT ck_ledger_financial_account_name_normalized CHECK (
        char_length(name_normalized) BETWEEN 1 AND 160
        AND name_normalized = btrim(name_normalized)
        AND name_normalized = upper(name_normalized)
        AND name_normalized = upper(name)
    ),
    CONSTRAINT ck_ledger_financial_account_kind CHECK (
        account_kind IN ('CASH_CURRENT', 'CASH_SAVINGS', 'CASH_WALLET', 'BROKERAGE', 'CREDIT_CARD', 'LOAN')
    ),
    CONSTRAINT ck_ledger_financial_account_tracking CHECK (
        tracking_mode IN ('FULL_LEDGER', 'HOLDINGS_ONLY')
    ),
    CONSTRAINT ck_ledger_financial_account_policy_shape CHECK (
        (
            tracking_mode = 'FULL_LEDGER'
            AND account_kind IN ('CASH_CURRENT', 'CASH_SAVINGS', 'CASH_WALLET', 'BROKERAGE')
            AND negative_balance_policy IN ('HARD_FLOOR', 'SOFT_FLOOR', 'TRACK_REALITY', 'AUTHORIZED_LIMIT')
        )
        OR (
            tracking_mode = 'FULL_LEDGER'
            AND account_kind IN ('CREDIT_CARD', 'LOAN')
            AND negative_balance_policy IS NULL
        )
        OR (
            tracking_mode = 'HOLDINGS_ONLY'
            AND account_kind = 'BROKERAGE'
            AND negative_balance_policy IS NULL
        )
    ),
    CONSTRAINT ck_ledger_financial_account_authorized_limit CHECK (
        (
            authorized_limit IS NULL
            AND negative_balance_policy IS DISTINCT FROM 'AUTHORIZED_LIMIT'
        )
        OR (
            authorized_limit IS NOT NULL
            AND account_kind = 'CASH_CURRENT'
            AND negative_balance_policy = 'AUTHORIZED_LIMIT'
            AND authorized_limit > 0
        )
    ),
    CONSTRAINT ck_ledger_financial_account_time_zone CHECK (
        char_length(btrim(time_zone)) > 0
        AND time_zone = btrim(time_zone)
    ),
    CONSTRAINT ck_ledger_financial_account_version_non_negative CHECK (version >= 0)
);

CREATE INDEX ix_ledger_financial_account_owner_name
    ON ledger.financial_account (owner_user_account_id, name_normalized, id);

CREATE UNIQUE INDEX uix_ledger_financial_account_active_name
    ON ledger.financial_account (owner_user_account_id, name_normalized)
    WHERE archived_at IS NULL;

CREATE TABLE ledger.activity (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    client_event_id uuid NOT NULL,
    operation_scope text NOT NULL,
    command_sequence bigint NOT NULL DEFAULT 0,
    activity_type text NOT NULL,
    recording_mode text NOT NULL,
    effective_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL,
    economic_sequence bigint,
    source_kind text NOT NULL,
    policy_decision text NOT NULL,
    correction_reason text,
    reverses_activity_id uuid,
    supersedes_activity_id uuid,
    CONSTRAINT pk_ledger_activity PRIMARY KEY (id),
    CONSTRAINT fk_ledger_activity_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_activity_reverses FOREIGN KEY (owner_user_account_id, reverses_activity_id)
        REFERENCES ledger.activity (owner_user_account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_activity_supersedes FOREIGN KEY (owner_user_account_id, supersedes_activity_id)
        REFERENCES ledger.activity (owner_user_account_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_ledger_activity_owner_id UNIQUE (owner_user_account_id, id),
    CONSTRAINT uq_ledger_activity_operation UNIQUE (
        owner_user_account_id, operation_scope, client_event_id, command_sequence
    ),
    CONSTRAINT uq_ledger_activity_reversal UNIQUE (reverses_activity_id),
    CONSTRAINT ck_ledger_activity_operation_scope CHECK (
        char_length(operation_scope) BETWEEN 1 AND 120
        AND operation_scope = btrim(operation_scope)
    ),
    CONSTRAINT ck_ledger_activity_command_sequence CHECK (command_sequence >= 0),
    CONSTRAINT ck_ledger_activity_economic_sequence CHECK (
        economic_sequence IS NULL OR economic_sequence >= 0
    ),
    CONSTRAINT ck_ledger_activity_type CHECK (
        activity_type IN ('OPENING_BALANCE', 'CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'OWNED_TRANSFER', 'REVERSAL')
    ),
    CONSTRAINT ck_ledger_activity_recording_mode CHECK (
        recording_mode IN ('CURRENT_ACTION', 'HISTORICAL_FACT')
    ),
    CONSTRAINT ck_ledger_activity_source_kind CHECK (source_kind = 'USER_ENTERED'),
    CONSTRAINT ck_ledger_activity_policy_decision CHECK (
        policy_decision IN ('NOT_APPLICABLE', 'ALLOWED', 'CONFIRMED_BREACH', 'HISTORICAL_BREACH_RECORDED')
    ),
    CONSTRAINT ck_ledger_activity_policy_shape CHECK (
        (
            activity_type = 'REVERSAL'
            AND recording_mode = 'HISTORICAL_FACT'
            AND policy_decision = 'NOT_APPLICABLE'
        )
        OR (
            activity_type = 'OPENING_BALANCE'
            AND recording_mode = 'HISTORICAL_FACT'
            AND policy_decision IN ('ALLOWED', 'HISTORICAL_BREACH_RECORDED')
        )
        OR (
            activity_type IN ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'OWNED_TRANSFER')
            AND recording_mode = 'CURRENT_ACTION'
            AND policy_decision IN ('ALLOWED', 'CONFIRMED_BREACH', 'HISTORICAL_BREACH_RECORDED')
        )
        OR (
            activity_type IN ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'OWNED_TRANSFER')
            AND recording_mode = 'HISTORICAL_FACT'
            AND policy_decision IN ('ALLOWED', 'HISTORICAL_BREACH_RECORDED')
        )
    ),
    CONSTRAINT ck_ledger_activity_correction_reason CHECK (
        correction_reason IS NULL OR char_length(correction_reason) BETWEEN 1 AND 500
    ),
    CONSTRAINT ck_ledger_activity_reversal_shape CHECK (
        (reverses_activity_id IS NULL AND activity_type <> 'REVERSAL')
        OR (reverses_activity_id IS NOT NULL AND activity_type = 'REVERSAL')
    ),
    CONSTRAINT ck_ledger_activity_no_self_link CHECK (
        (reverses_activity_id IS NULL OR reverses_activity_id <> id)
        AND (supersedes_activity_id IS NULL OR supersedes_activity_id <> id)
    ),
    CONSTRAINT ck_ledger_activity_supersession_shape CHECK (
        supersedes_activity_id IS NULL OR activity_type = 'OPENING_BALANCE'
    )
);

CREATE INDEX ix_ledger_activity_owner_effective
    ON ledger.activity (owner_user_account_id, effective_at, id);

CREATE INDEX ix_ledger_activity_owner_recorded
    ON ledger.activity (owner_user_account_id, recorded_at, id);

CREATE TABLE ledger.account_cash_pocket (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    financial_account_id uuid NOT NULL,
    currency_code text NOT NULL,
    coverage_status text NOT NULL,
    coverage_from timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_ledger_account_cash_pocket PRIMARY KEY (id),
    CONSTRAINT fk_ledger_account_cash_pocket_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_account_cash_pocket_account_owner FOREIGN KEY (
        owner_user_account_id, financial_account_id
    ) REFERENCES ledger.financial_account (owner_user_account_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_account_cash_pocket_account_currency FOREIGN KEY (
        financial_account_id, currency_code
    ) REFERENCES ledger.financial_account (id, currency_code) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_account_cash_pocket_currency FOREIGN KEY (currency_code)
        REFERENCES reference.currency (code) ON DELETE RESTRICT,
    CONSTRAINT uq_ledger_account_cash_pocket_account_currency UNIQUE (financial_account_id, currency_code),
    CONSTRAINT uq_ledger_account_cash_pocket_owner_id UNIQUE (owner_user_account_id, id),
    CONSTRAINT uq_ledger_account_cash_pocket_identity UNIQUE (
        owner_user_account_id, id, financial_account_id, currency_code
    ),
    CONSTRAINT ck_ledger_account_cash_pocket_coverage_status CHECK (
        coverage_status = 'KNOWN_FROM_OPENING'
    ),
    CONSTRAINT ck_ledger_account_cash_pocket_version_non_negative CHECK (version >= 0)
);

CREATE INDEX ix_ledger_account_cash_pocket_owner_account
    ON ledger.account_cash_pocket (owner_user_account_id, financial_account_id);

    ALTER TABLE ledger.financial_account
    ADD CONSTRAINT fk_ledger_financial_account_current_opening
    FOREIGN KEY (owner_user_account_id, current_opening_activity_id)
    REFERENCES ledger.activity (owner_user_account_id, id) ON DELETE RESTRICT;

CREATE TABLE ledger.money_posting (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    activity_id uuid NOT NULL,
    financial_account_id uuid NOT NULL,
    cash_pocket_id uuid NOT NULL,
    currency_code text NOT NULL,
    amount numeric(38, 18) NOT NULL,
    posting_role text NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_ledger_money_posting PRIMARY KEY (id),
    CONSTRAINT fk_ledger_money_posting_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_money_posting_activity FOREIGN KEY (owner_user_account_id, activity_id)
        REFERENCES ledger.activity (owner_user_account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_money_posting_account FOREIGN KEY (owner_user_account_id, financial_account_id)
        REFERENCES ledger.financial_account (owner_user_account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_money_posting_pocket FOREIGN KEY (owner_user_account_id, cash_pocket_id)
        REFERENCES ledger.account_cash_pocket (owner_user_account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_money_posting_account_currency FOREIGN KEY (financial_account_id, currency_code)
        REFERENCES ledger.financial_account (id, currency_code) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_money_posting_pocket_identity FOREIGN KEY (
        owner_user_account_id, cash_pocket_id, financial_account_id, currency_code
    ) REFERENCES ledger.account_cash_pocket (
        owner_user_account_id, id, financial_account_id, currency_code
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_ledger_money_posting_amount_zero_shape CHECK (
        amount <> 0 OR posting_role IN ('OPENING', 'REVERSAL')
    ),
    CONSTRAINT ck_ledger_money_posting_role_sign CHECK (
        posting_role IN ('OPENING', 'REVERSAL')
        OR (posting_role IN ('DEPOSIT', 'TRANSFER_DESTINATION') AND amount > 0)
        OR (posting_role IN ('WITHDRAWAL', 'TRANSFER_SOURCE') AND amount < 0)
    ),
    CONSTRAINT ck_ledger_money_posting_role CHECK (
        posting_role IN ('OPENING', 'DEPOSIT', 'WITHDRAWAL', 'TRANSFER_SOURCE', 'TRANSFER_DESTINATION', 'REVERSAL')
    )
);

CREATE INDEX ix_ledger_money_posting_activity ON ledger.money_posting (owner_user_account_id, activity_id);
CREATE INDEX ix_ledger_money_posting_account ON ledger.money_posting (owner_user_account_id, financial_account_id);

CREATE TABLE ledger.idempotency_record (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    operation_scope text NOT NULL,
    client_request_id uuid NOT NULL,
    request_hash text NOT NULL,
    result_resource_kind text NOT NULL,
    result_resource_id uuid NOT NULL,
    result_snapshot jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_ledger_idempotency_record PRIMARY KEY (id),
    CONSTRAINT fk_ledger_idempotency_record_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT uq_ledger_idempotency_owner_scope_request UNIQUE (
        owner_user_account_id, operation_scope, client_request_id
    ),
    CONSTRAINT ck_ledger_idempotency_operation_scope CHECK (
        char_length(operation_scope) BETWEEN 1 AND 120
        AND operation_scope = btrim(operation_scope)
    ),
    CONSTRAINT ck_ledger_idempotency_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ledger_idempotency_result_kind CHECK (
        char_length(result_resource_kind) BETWEEN 1 AND 80
        AND result_resource_kind = btrim(result_resource_kind)
    ),
    CONSTRAINT ck_ledger_idempotency_snapshot_object CHECK (jsonb_typeof(result_snapshot) = 'object'),
    CONSTRAINT ck_ledger_idempotency_snapshot_size CHECK (octet_length(result_snapshot::text) <= 32768)
);

CREATE TABLE ledger.account_balance_projection (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    financial_account_id uuid NOT NULL,
    cash_pocket_id uuid NOT NULL,
    currency_code text NOT NULL,
    ledger_balance numeric(38, 18) NOT NULL DEFAULT 0,
    last_applied_recorded_at timestamptz NOT NULL,
    last_applied_activity_id uuid NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_ledger_account_balance_projection PRIMARY KEY (id),
    CONSTRAINT fk_ledger_account_balance_projection_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_account_balance_projection_pocket FOREIGN KEY (
        owner_user_account_id, cash_pocket_id
    ) REFERENCES ledger.account_cash_pocket (owner_user_account_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_account_balance_projection_account FOREIGN KEY (
        owner_user_account_id, financial_account_id
    ) REFERENCES ledger.financial_account (owner_user_account_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_account_balance_projection_account_currency FOREIGN KEY (
        financial_account_id, currency_code
    ) REFERENCES ledger.financial_account (id, currency_code) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_account_balance_projection_pocket_identity FOREIGN KEY (
        owner_user_account_id, cash_pocket_id, financial_account_id, currency_code
    ) REFERENCES ledger.account_cash_pocket (
        owner_user_account_id, id, financial_account_id, currency_code
    ) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_account_balance_projection_watermark_activity FOREIGN KEY (
        owner_user_account_id, last_applied_activity_id
    ) REFERENCES ledger.activity (owner_user_account_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_ledger_account_balance_projection_pocket UNIQUE (owner_user_account_id, cash_pocket_id),
    CONSTRAINT ck_ledger_account_balance_projection_version_non_negative CHECK (version >= 0)
);

CREATE INDEX ix_ledger_account_balance_projection_owner_account
    ON ledger.account_balance_projection (owner_user_account_id, financial_account_id);
