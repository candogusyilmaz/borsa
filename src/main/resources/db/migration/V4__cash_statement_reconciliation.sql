-- V4 immutable statement reconciliation evidence and explicit adjustment support.

ALTER TABLE ledger.activity
    DROP CONSTRAINT ck_ledger_activity_type,
    DROP CONSTRAINT ck_ledger_activity_policy_shape;

ALTER TABLE ledger.activity
    ADD CONSTRAINT ck_ledger_activity_type CHECK (
        activity_type IN (
            'OPENING_BALANCE',
            'CASH_DEPOSIT',
            'CASH_WITHDRAWAL',
            'OWNED_TRANSFER',
            'REVERSAL',
            'RECONCILIATION_ADJUSTMENT'
        )
    ),
    ADD CONSTRAINT ck_ledger_activity_policy_shape CHECK (
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
        OR (
            activity_type = 'RECONCILIATION_ADJUSTMENT'
            AND recording_mode = 'HISTORICAL_FACT'
            AND policy_decision IN ('ALLOWED', 'HISTORICAL_BREACH_RECORDED')
            AND correction_reason IS NOT NULL
            AND char_length(btrim(correction_reason)) BETWEEN 1 AND 500
            AND correction_reason = btrim(correction_reason)
        )
    );

ALTER TABLE ledger.money_posting
    DROP CONSTRAINT ck_ledger_money_posting_role_sign,
    DROP CONSTRAINT ck_ledger_money_posting_role;

ALTER TABLE ledger.money_posting
    ADD CONSTRAINT ck_ledger_money_posting_role_sign CHECK (
        posting_role IN ('OPENING', 'REVERSAL', 'ADJUSTMENT')
        OR (posting_role IN ('DEPOSIT', 'TRANSFER_DESTINATION') AND amount > 0)
        OR (posting_role IN ('WITHDRAWAL', 'TRANSFER_SOURCE') AND amount < 0)
    ),
    ADD CONSTRAINT ck_ledger_money_posting_role CHECK (
        posting_role IN (
            'OPENING',
            'DEPOSIT',
            'WITHDRAWAL',
            'TRANSFER_SOURCE',
            'TRANSFER_DESTINATION',
            'REVERSAL',
            'ADJUSTMENT'
        )
    );

ALTER TABLE ledger.activity
    ADD CONSTRAINT uq_ledger_activity_owner_id_type UNIQUE (owner_user_account_id, id, activity_type);

CREATE TABLE ledger.reconciliation (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    financial_account_id uuid NOT NULL,
    cash_pocket_id uuid NOT NULL,
    currency_code text NOT NULL,
    statement_reference text NOT NULL,
    statement_opening_at timestamptz NOT NULL,
    statement_closing_at timestamptz NOT NULL,
    statement_opening_balance numeric(38, 18) NOT NULL,
    statement_closing_balance numeric(38, 18) NOT NULL,
    ledger_opening_balance numeric(38, 18) NOT NULL,
    ledger_closing_balance_before_adjustment numeric(38, 18) NOT NULL,
    period_net_posted_amount numeric(38, 18) NOT NULL,
    closing_difference numeric(38, 18) NOT NULL,
    adjustment_amount numeric(38, 18),
    period_posting_count bigint NOT NULL,
    total_posting_count_through_closing bigint NOT NULL,
    resolution text NOT NULL,
    adjustment_activity_id uuid,
    adjustment_activity_type text GENERATED ALWAYS AS ('RECONCILIATION_ADJUSTMENT') STORED,
    supersedes_reconciliation_id uuid,
    source_kind text NOT NULL,
    adjustment_reason text,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_ledger_reconciliation PRIMARY KEY (id),
    CONSTRAINT fk_ledger_reconciliation_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_reconciliation_account_owner FOREIGN KEY (
        owner_user_account_id, financial_account_id
    ) REFERENCES ledger.financial_account (owner_user_account_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_reconciliation_account_currency FOREIGN KEY (
        financial_account_id, currency_code
    ) REFERENCES ledger.financial_account (id, currency_code) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_reconciliation_pocket_identity FOREIGN KEY (
        owner_user_account_id, cash_pocket_id, financial_account_id, currency_code
    ) REFERENCES ledger.account_cash_pocket (
        owner_user_account_id, id, financial_account_id, currency_code
    ) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_reconciliation_adjustment_activity FOREIGN KEY (
        owner_user_account_id, adjustment_activity_id, adjustment_activity_type
    ) REFERENCES ledger.activity (owner_user_account_id, id, activity_type) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_reconciliation_supersedes FOREIGN KEY (
        owner_user_account_id, financial_account_id, supersedes_reconciliation_id
    ) REFERENCES ledger.reconciliation (owner_user_account_id, financial_account_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_ledger_reconciliation_owner_id UNIQUE (owner_user_account_id, id),
    CONSTRAINT uq_ledger_reconciliation_owner_account_id UNIQUE (
        owner_user_account_id, financial_account_id, id
    ),
    CONSTRAINT uq_ledger_reconciliation_adjustment_activity UNIQUE (adjustment_activity_id),
    CONSTRAINT uq_ledger_reconciliation_supersedes UNIQUE (
        owner_user_account_id, supersedes_reconciliation_id
    ),
    CONSTRAINT ck_ledger_reconciliation_statement_reference CHECK (
        char_length(statement_reference) BETWEEN 1 AND 200
        AND statement_reference = btrim(statement_reference)
    ),
    CONSTRAINT ck_ledger_reconciliation_time_order CHECK (statement_opening_at < statement_closing_at),
    CONSTRAINT ck_ledger_reconciliation_counts CHECK (
        period_posting_count >= 0 AND total_posting_count_through_closing >= 0
    ),
    CONSTRAINT ck_ledger_reconciliation_resolution CHECK (
        resolution IN ('BALANCED', 'ADJUSTED')
    ),
    CONSTRAINT ck_ledger_reconciliation_source_kind CHECK (source_kind = 'USER_ENTERED'),
    CONSTRAINT ck_ledger_reconciliation_no_self_supersession CHECK (
        supersedes_reconciliation_id IS NULL OR supersedes_reconciliation_id <> id
    ),
    CONSTRAINT ck_ledger_reconciliation_equation CHECK (
        statement_opening_balance = ledger_opening_balance
        AND ledger_opening_balance + period_net_posted_amount = ledger_closing_balance_before_adjustment
    ),
    CONSTRAINT ck_ledger_reconciliation_resolution_shape CHECK (
        (
            resolution = 'BALANCED'
            AND closing_difference = 0
            AND adjustment_amount IS NULL
            AND adjustment_activity_id IS NULL
            AND adjustment_reason IS NULL
            AND ledger_closing_balance_before_adjustment = statement_closing_balance
        )
        OR (
            resolution = 'ADJUSTED'
            AND closing_difference <> 0
            AND adjustment_amount IS NOT NULL
            AND adjustment_amount <> 0
            AND adjustment_activity_id IS NOT NULL
            AND adjustment_reason IS NOT NULL
            AND char_length(adjustment_reason) BETWEEN 1 AND 500
            AND adjustment_reason = btrim(adjustment_reason)
            AND adjustment_amount = closing_difference
            AND ledger_closing_balance_before_adjustment + adjustment_amount = statement_closing_balance
        )
    )
);

CREATE INDEX ix_ledger_reconciliation_owner_account_closing
    ON ledger.reconciliation (
        owner_user_account_id,
        financial_account_id,
        statement_closing_at DESC,
        id DESC
    );

CREATE OR REPLACE FUNCTION ledger.reject_reconciliation_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'ledger.reconciliation rows are immutable' USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN
        IF NOT EXISTS (
            SELECT 1
            FROM identity.user_account owner_account
            WHERE owner_account.id = OLD.owner_user_account_id
        ) THEN
            RETURN OLD;
        END IF;
        RAISE EXCEPTION 'ledger.reconciliation rows are immutable' USING ERRCODE = '55000';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_ledger_reconciliation_append_only
    BEFORE UPDATE OR DELETE ON ledger.reconciliation
    FOR EACH ROW
    EXECUTE FUNCTION ledger.reject_reconciliation_mutation();
