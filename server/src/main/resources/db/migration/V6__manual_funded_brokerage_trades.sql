-- V6 immutable funded trades and synchronous position projections.

ALTER TABLE ledger.activity
    DROP CONSTRAINT ck_ledger_activity_type,
    DROP CONSTRAINT ck_ledger_activity_policy_shape;

ALTER TABLE ledger.activity
    ADD CONSTRAINT ck_ledger_activity_type CHECK (
        activity_type IN (
            'OPENING_BALANCE',
            'CASH_DEPOSIT',
            'CASH_WITHDRAWAL',
            'CASH_FEE',
            'CASH_INTEREST_CREDIT',
            'OWNED_TRANSFER',
            'SECURITY_BUY',
            'SECURITY_SELL',
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
            activity_type IN (
                'CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'CASH_FEE', 'CASH_INTEREST_CREDIT', 'OWNED_TRANSFER',
                'SECURITY_BUY', 'SECURITY_SELL'
            )
            AND recording_mode = 'CURRENT_ACTION'
            AND policy_decision IN ('ALLOWED', 'CONFIRMED_BREACH', 'HISTORICAL_BREACH_RECORDED')
        )
        OR (
            activity_type IN (
                'CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'CASH_FEE', 'CASH_INTEREST_CREDIT', 'OWNED_TRANSFER',
                'SECURITY_BUY', 'SECURITY_SELL'
            )
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
    ),
    ADD CONSTRAINT ck_ledger_activity_trade_sequence CHECK (
        activity_type NOT IN ('SECURITY_BUY', 'SECURITY_SELL') OR (economic_sequence IS NOT NULL AND economic_sequence >= 0)
    );

ALTER TABLE ledger.money_posting
    ADD COLUMN reverses_money_posting_id uuid,
    ADD CONSTRAINT uq_ledger_money_posting_owner_id UNIQUE (owner_user_account_id, id),
    ADD CONSTRAINT fk_ledger_money_posting_reverses FOREIGN KEY (owner_user_account_id, reverses_money_posting_id)
        REFERENCES ledger.money_posting (owner_user_account_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_ledger_money_posting_reversal_link CHECK (
        reverses_money_posting_id IS NULL OR posting_role = 'REVERSAL'
    );

ALTER TABLE ledger.money_posting
    DROP CONSTRAINT ck_ledger_money_posting_role_sign,
    DROP CONSTRAINT ck_ledger_money_posting_role;

ALTER TABLE ledger.money_posting
    ADD CONSTRAINT ck_ledger_money_posting_role_sign CHECK (
        posting_role IN ('OPENING', 'REVERSAL', 'ADJUSTMENT')
        OR (posting_role IN ('DEPOSIT', 'TRANSFER_DESTINATION', 'INTEREST_CREDIT', 'TRADE_PROCEEDS') AND amount > 0)
        OR (posting_role IN ('WITHDRAWAL', 'TRANSFER_SOURCE', 'FEE', 'TRADE_PURCHASE') AND amount < 0)
    ),
    ADD CONSTRAINT ck_ledger_money_posting_role CHECK (
        posting_role IN (
            'OPENING',
            'DEPOSIT',
            'WITHDRAWAL',
            'FEE',
            'INTEREST_CREDIT',
            'TRANSFER_SOURCE',
            'TRANSFER_DESTINATION',
            'TRADE_PURCHASE',
            'TRADE_PROCEEDS',
            'REVERSAL',
            'ADJUSTMENT'
        )
    );

CREATE UNIQUE INDEX uix_ledger_money_posting_activity_trade_role
    ON ledger.money_posting (owner_user_account_id, activity_id, posting_role)
    WHERE posting_role IN ('TRADE_PURCHASE', 'TRADE_PROCEEDS', 'FEE');

ALTER TABLE reference.instrument
    ADD CONSTRAINT uq_reference_instrument_id_currency UNIQUE (id, quotation_currency_code);

CREATE TABLE ledger.security_posting (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    activity_id uuid NOT NULL,
    financial_account_id uuid NOT NULL,
    instrument_id uuid NOT NULL,
    trade_currency_code text NOT NULL,
    quantity_delta numeric(38, 18) NOT NULL,
    unit_price numeric(38, 18),
    gross_amount numeric(38, 18),
    posting_role text NOT NULL,
    effective_at timestamptz NOT NULL,
    economic_sequence bigint NOT NULL,
    reverses_security_posting_id uuid,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_ledger_security_posting PRIMARY KEY (id),
    CONSTRAINT fk_ledger_security_posting_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_security_posting_activity FOREIGN KEY (owner_user_account_id, activity_id)
        REFERENCES ledger.activity (owner_user_account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_security_posting_account FOREIGN KEY (owner_user_account_id, financial_account_id)
        REFERENCES ledger.financial_account (owner_user_account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_security_posting_account_currency FOREIGN KEY (financial_account_id, trade_currency_code)
        REFERENCES ledger.financial_account (id, currency_code) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_security_posting_instrument FOREIGN KEY (instrument_id)
        REFERENCES reference.instrument (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_security_posting_instrument_currency FOREIGN KEY (instrument_id, trade_currency_code)
        REFERENCES reference.instrument (id, quotation_currency_code) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_security_posting_currency FOREIGN KEY (trade_currency_code)
        REFERENCES reference.currency (code) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_security_posting_reverses FOREIGN KEY (owner_user_account_id, reverses_security_posting_id)
        REFERENCES ledger.security_posting (owner_user_account_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_ledger_security_posting_owner_id UNIQUE (owner_user_account_id, id),
    CONSTRAINT uq_ledger_security_posting_owner_activity UNIQUE (owner_user_account_id, activity_id),
    CONSTRAINT uq_ledger_security_posting_reversal UNIQUE (reverses_security_posting_id),
    CONSTRAINT ck_ledger_security_posting_role CHECK (posting_role IN ('BUY', 'SELL', 'REVERSAL')),
    CONSTRAINT ck_ledger_security_posting_economic_sequence CHECK (economic_sequence >= 0),
    CONSTRAINT ck_ledger_security_posting_shape CHECK (
        (
            posting_role = 'BUY'
            AND quantity_delta > 0
            AND unit_price IS NOT NULL
            AND unit_price > 0
            AND gross_amount IS NOT NULL
            AND gross_amount > 0
            AND reverses_security_posting_id IS NULL
        )
        OR (
            posting_role = 'SELL'
            AND quantity_delta < 0
            AND unit_price IS NOT NULL
            AND unit_price > 0
            AND gross_amount IS NOT NULL
            AND gross_amount > 0
            AND reverses_security_posting_id IS NULL
        )
        OR (
            posting_role = 'REVERSAL'
            AND quantity_delta <> 0
            AND unit_price IS NULL
            AND gross_amount IS NULL
            AND reverses_security_posting_id IS NOT NULL
        )
    )
);

CREATE UNIQUE INDEX uix_ledger_security_posting_economic_key
    ON ledger.security_posting (owner_user_account_id, financial_account_id, instrument_id, effective_at, economic_sequence)
    WHERE posting_role IN ('BUY', 'SELL');

CREATE INDEX ix_ledger_security_posting_owner_account_instrument_order
    ON ledger.security_posting (owner_user_account_id, financial_account_id, instrument_id, effective_at, economic_sequence, activity_id);

CREATE TABLE ledger.position_projection (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    financial_account_id uuid NOT NULL,
    instrument_id uuid NOT NULL,
    currency_code text NOT NULL,
    current_quantity numeric(38, 18) NOT NULL,
    remaining_economic_basis numeric(38, 18) NOT NULL,
    cumulative_realized_economic_pnl numeric(38, 18) NOT NULL,
    calculation_policy text NOT NULL,
    projection_status text NOT NULL,
    as_of timestamptz NOT NULL,
    input_watermark_activity_id uuid NOT NULL,
    last_successful_build_at timestamptz NOT NULL,
    stale_from timestamptz,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_ledger_position_projection PRIMARY KEY (id),
    CONSTRAINT fk_ledger_position_projection_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_position_projection_account FOREIGN KEY (owner_user_account_id, financial_account_id)
        REFERENCES ledger.financial_account (owner_user_account_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_position_projection_account_currency FOREIGN KEY (financial_account_id, currency_code)
        REFERENCES ledger.financial_account (id, currency_code) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_position_projection_instrument FOREIGN KEY (instrument_id)
        REFERENCES reference.instrument (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_position_projection_instrument_currency FOREIGN KEY (instrument_id, currency_code)
        REFERENCES reference.instrument (id, quotation_currency_code) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_position_projection_currency FOREIGN KEY (currency_code)
        REFERENCES reference.currency (code) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_position_projection_watermark_activity FOREIGN KEY (owner_user_account_id, input_watermark_activity_id)
        REFERENCES ledger.activity (owner_user_account_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_ledger_position_projection_owner_account_instrument UNIQUE (owner_user_account_id, financial_account_id, instrument_id),
    CONSTRAINT ck_ledger_position_projection_quantity_non_negative CHECK (current_quantity >= 0),
    CONSTRAINT ck_ledger_position_projection_basis_non_negative CHECK (remaining_economic_basis >= 0),
    CONSTRAINT ck_ledger_position_projection_close_basis CHECK (current_quantity <> 0 OR remaining_economic_basis = 0),
    CONSTRAINT ck_ledger_position_projection_calculation_policy CHECK (calculation_policy = 'WEIGHTED_AVERAGE_ECONOMIC_V1'),
    CONSTRAINT ck_ledger_position_projection_status CHECK (projection_status IN ('CURRENT', 'STALE', 'REBUILDING', 'FAILED')),
    CONSTRAINT ck_ledger_position_projection_stale_shape CHECK (
        (projection_status = 'CURRENT' AND stale_from IS NULL)
        OR (projection_status <> 'CURRENT' AND stale_from IS NOT NULL)
    ),
    CONSTRAINT ck_ledger_position_projection_version_non_negative CHECK (version >= 0)
);

CREATE INDEX ix_ledger_position_projection_owner_account_open
    ON ledger.position_projection (owner_user_account_id, financial_account_id, instrument_id)
    WHERE current_quantity > 0;

CREATE OR REPLACE FUNCTION ledger.validate_position_projection_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_account_owner_id uuid;
    v_account_kind text;
    v_tracking_mode text;
    v_account_currency text;
    v_instrument_owner_id uuid;
    v_instrument_type text;
    v_instrument_currency text;
BEGIN
    IF TG_OP = 'UPDATE' AND (
        NEW.id IS DISTINCT FROM OLD.id OR
        NEW.owner_user_account_id IS DISTINCT FROM OLD.owner_user_account_id OR
        NEW.financial_account_id IS DISTINCT FROM OLD.financial_account_id OR
        NEW.instrument_id IS DISTINCT FROM OLD.instrument_id OR
        NEW.currency_code IS DISTINCT FROM OLD.currency_code
    ) THEN
        RAISE EXCEPTION 'Position projection identity is immutable' USING ERRCODE = '23514',
            CONSTRAINT = 'ck_ledger_position_projection_identity';
    END IF;

    SELECT a.owner_user_account_id, a.account_kind, a.tracking_mode, a.currency_code,
           i.owner_user_account_id, i.instrument_type, i.quotation_currency_code
      INTO v_account_owner_id, v_account_kind, v_tracking_mode, v_account_currency,
           v_instrument_owner_id, v_instrument_type, v_instrument_currency
      FROM ledger.financial_account a
      JOIN reference.instrument i ON i.id = NEW.instrument_id
     WHERE a.id = NEW.financial_account_id;
    IF NOT FOUND OR v_account_owner_id IS DISTINCT FROM NEW.owner_user_account_id OR
            v_account_kind <> 'BROKERAGE' OR v_tracking_mode <> 'FULL_LEDGER' OR
            v_account_currency <> NEW.currency_code OR v_instrument_currency <> NEW.currency_code OR
            v_instrument_type NOT IN ('EQUITY', 'ETF') OR
            (v_instrument_owner_id IS NOT NULL AND v_instrument_owner_id <> NEW.owner_user_account_id) THEN
        RAISE EXCEPTION 'Position projection identity is unsupported' USING ERRCODE = '23514',
            CONSTRAINT = 'ck_ledger_position_projection_identity';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_ledger_validate_position_projection_identity
    BEFORE INSERT OR UPDATE ON ledger.position_projection
    FOR EACH ROW
    EXECUTE FUNCTION ledger.validate_position_projection_identity();

CREATE OR REPLACE FUNCTION ledger.reject_financial_fact_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION '% rows are immutable', TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN
        IF NOT EXISTS (
            SELECT 1
            FROM identity.user_account owner_account
            WHERE owner_account.id = OLD.owner_user_account_id
        ) THEN
            RETURN OLD;
        END IF;
        RAISE EXCEPTION '% rows are immutable', TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME USING ERRCODE = '55000';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_ledger_activity_append_only
    BEFORE UPDATE OR DELETE ON ledger.activity
    FOR EACH ROW
    EXECUTE FUNCTION ledger.reject_financial_fact_mutation();

CREATE TRIGGER trg_ledger_money_posting_append_only
    BEFORE UPDATE OR DELETE ON ledger.money_posting
    FOR EACH ROW
    EXECUTE FUNCTION ledger.reject_financial_fact_mutation();

CREATE TRIGGER trg_ledger_security_posting_append_only
    BEFORE UPDATE OR DELETE ON ledger.security_posting
    FOR EACH ROW
    EXECUTE FUNCTION ledger.reject_financial_fact_mutation();

CREATE OR REPLACE FUNCTION ledger.validate_trade_money_posting()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_activity_type text;
    v_reverses_activity_id uuid;
BEGIN
    SELECT activity_type, reverses_activity_id
      INTO v_activity_type, v_reverses_activity_id
      FROM ledger.activity
     WHERE owner_user_account_id = NEW.owner_user_account_id
       AND id = NEW.activity_id;

    IF NEW.posting_role = 'TRADE_PURCHASE' AND v_activity_type <> 'SECURITY_BUY' THEN
        RAISE EXCEPTION 'TRADE_PURCHASE must belong to SECURITY_BUY' USING ERRCODE = '23514', CONSTRAINT = 'ck_ledger_money_posting_trade_activity';
    END IF;
    IF NEW.posting_role = 'TRADE_PROCEEDS' AND v_activity_type <> 'SECURITY_SELL' THEN
        RAISE EXCEPTION 'TRADE_PROCEEDS must belong to SECURITY_SELL' USING ERRCODE = '23514', CONSTRAINT = 'ck_ledger_money_posting_trade_activity';
    END IF;
    IF NEW.posting_role = 'FEE' AND v_activity_type NOT IN ('CASH_FEE', 'SECURITY_BUY', 'SECURITY_SELL') THEN
        RAISE EXCEPTION 'FEE must belong to a cash fee or trade activity' USING ERRCODE = '23514', CONSTRAINT = 'ck_ledger_money_posting_trade_activity';
    END IF;

    IF NEW.reverses_money_posting_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
          FROM ledger.money_posting original_posting
          JOIN ledger.activity reversal_activity
            ON reversal_activity.owner_user_account_id = NEW.owner_user_account_id
           AND reversal_activity.id = NEW.activity_id
         WHERE original_posting.owner_user_account_id = NEW.owner_user_account_id
           AND original_posting.id = NEW.reverses_money_posting_id
           AND original_posting.posting_role <> 'REVERSAL'
           AND reversal_activity.activity_type = 'REVERSAL'
           AND reversal_activity.reverses_activity_id = original_posting.activity_id
           AND original_posting.financial_account_id = NEW.financial_account_id
           AND original_posting.cash_pocket_id = NEW.cash_pocket_id
           AND original_posting.currency_code = NEW.currency_code
           AND NEW.amount = -original_posting.amount
    ) THEN
        RAISE EXCEPTION 'Linked reversal cash posting must exactly invert its original' USING ERRCODE = '23514',
            CONSTRAINT = 'ck_ledger_money_posting_reversal_link_shape';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_ledger_validate_trade_money_posting
    BEFORE INSERT ON ledger.money_posting
    FOR EACH ROW
    EXECUTE FUNCTION ledger.validate_trade_money_posting();

CREATE OR REPLACE FUNCTION ledger.validate_security_posting()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_activity_type text;
    v_recording_mode text;
    v_effective_at timestamptz;
    v_economic_sequence bigint;
    v_reverses_activity_id uuid;
    v_original_activity_id uuid;
    v_original_account_id uuid;
    v_original_instrument_id uuid;
    v_original_currency text;
    v_original_quantity numeric(38, 18);
    v_original_effective_at timestamptz;
    v_original_sequence bigint;
    v_instrument_owner_id uuid;
    v_instrument_type text;
    v_instrument_currency text;
    v_instrument_active boolean;
    v_account_owner_id uuid;
    v_account_kind text;
    v_tracking_mode text;
    v_archived_at timestamptz;
    v_account_currency text;
BEGIN
    SELECT activity_type, recording_mode, effective_at, economic_sequence, reverses_activity_id
      INTO v_activity_type, v_recording_mode, v_effective_at, v_economic_sequence, v_reverses_activity_id
      FROM ledger.activity
     WHERE owner_user_account_id = NEW.owner_user_account_id
       AND id = NEW.activity_id;

    IF NEW.effective_at IS DISTINCT FROM v_effective_at OR NEW.economic_sequence IS DISTINCT FROM v_economic_sequence THEN
        RAISE EXCEPTION 'Security posting order must match its activity' USING ERRCODE = '23514',
            CONSTRAINT = 'ck_ledger_security_posting_activity_order';
    END IF;

    IF NEW.posting_role = 'BUY' AND (v_activity_type <> 'SECURITY_BUY' OR NEW.reverses_security_posting_id IS NOT NULL) THEN
        RAISE EXCEPTION 'BUY posting must belong to a SECURITY_BUY activity' USING ERRCODE = '23514',
            CONSTRAINT = 'ck_ledger_security_posting_activity_shape';
    ELSIF NEW.posting_role = 'SELL' AND (v_activity_type <> 'SECURITY_SELL' OR NEW.reverses_security_posting_id IS NOT NULL) THEN
        RAISE EXCEPTION 'SELL posting must belong to a SECURITY_SELL activity' USING ERRCODE = '23514',
            CONSTRAINT = 'ck_ledger_security_posting_activity_shape';
    ELSIF NEW.posting_role = 'REVERSAL' THEN
        IF v_activity_type <> 'REVERSAL' OR v_reverses_activity_id IS NULL OR NEW.reverses_security_posting_id IS NULL THEN
            RAISE EXCEPTION 'REVERSAL posting must belong to a linked REVERSAL activity' USING ERRCODE = '23514',
                CONSTRAINT = 'ck_ledger_security_posting_reversal_shape';
        END IF;
        SELECT activity_id, financial_account_id, instrument_id, trade_currency_code, quantity_delta, effective_at, economic_sequence
          INTO v_original_activity_id, v_original_account_id, v_original_instrument_id, v_original_currency,
               v_original_quantity, v_original_effective_at, v_original_sequence
          FROM ledger.security_posting
         WHERE owner_user_account_id = NEW.owner_user_account_id
           AND id = NEW.reverses_security_posting_id
           AND posting_role IN ('BUY', 'SELL');
        IF NOT FOUND OR v_original_activity_id <> v_reverses_activity_id OR v_original_account_id <> NEW.financial_account_id OR
                v_original_instrument_id <> NEW.instrument_id OR v_original_currency <> NEW.trade_currency_code OR
                v_original_quantity <> -NEW.quantity_delta OR v_original_effective_at <> NEW.effective_at OR
                v_original_sequence <> NEW.economic_sequence THEN
            RAISE EXCEPTION 'Security reversal must exactly invert the linked original' USING ERRCODE = '23514',
                CONSTRAINT = 'ck_ledger_security_posting_reversal_shape';
        END IF;
    END IF;

    SELECT a.owner_user_account_id, a.account_kind, a.tracking_mode, a.archived_at, a.currency_code,
           i.owner_user_account_id, i.instrument_type, i.quotation_currency_code, i.active
      INTO v_account_owner_id, v_account_kind, v_tracking_mode, v_archived_at, v_account_currency,
           v_instrument_owner_id, v_instrument_type, v_instrument_currency, v_instrument_active
      FROM ledger.financial_account a
      JOIN reference.instrument i ON i.id = NEW.instrument_id
     WHERE a.id = NEW.financial_account_id;

    IF NOT FOUND OR v_account_owner_id <> NEW.owner_user_account_id OR v_account_kind <> 'BROKERAGE' OR
            v_tracking_mode <> 'FULL_LEDGER' OR (v_archived_at IS NOT NULL AND NEW.posting_role <> 'REVERSAL') OR
            v_account_currency <> NEW.trade_currency_code OR
            v_instrument_currency <> NEW.trade_currency_code OR v_instrument_type NOT IN ('EQUITY', 'ETF') OR
            (v_instrument_owner_id IS NOT NULL AND v_instrument_owner_id <> NEW.owner_user_account_id) THEN
        RAISE EXCEPTION 'Security posting account, instrument, owner, or currency is unsupported' USING ERRCODE = '23514',
            CONSTRAINT = 'ck_ledger_security_posting_instrument_account_shape';
    END IF;
    IF NEW.posting_role = 'BUY' AND v_activity_type = 'SECURITY_BUY' AND v_recording_mode = 'CURRENT_ACTION' AND NOT v_instrument_active THEN
        RAISE EXCEPTION 'Current security buys require an active instrument' USING ERRCODE = '23514',
            CONSTRAINT = 'ck_ledger_security_posting_active_buy';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_ledger_validate_security_posting
    BEFORE INSERT ON ledger.security_posting
    FOR EACH ROW
    EXECUTE FUNCTION ledger.validate_security_posting();

CREATE OR REPLACE FUNCTION ledger.validate_trade_activity_shape()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_activity_id uuid;
    v_owner_id uuid;
    v_activity_type text;
    v_reverses_activity_id uuid;
    v_security_count bigint;
    v_security_role text;
    v_gross numeric(38, 18);
    v_security_currency text;
    v_purchase_count bigint;
    v_proceeds_count bigint;
    v_fee_count bigint;
    v_money_count bigint;
    v_main_amount numeric(38, 18);
    v_fee_total numeric(38, 18);
    v_original_money_count bigint;
    v_reversal_money_count bigint;
    v_unlinked_reversal_count bigint;
    v_minor_unit smallint;
    v_security_account_id uuid;
    v_quantity_abs numeric(38, 18);
    v_unit_price numeric(38, 18);
    v_raw_gross numeric;
    v_scaled_gross numeric;
    v_currency_scale numeric;
    v_half_even_gross numeric;
BEGIN
    IF TG_TABLE_NAME = 'activity' THEN
        v_activity_id := NEW.id;
        v_owner_id := NEW.owner_user_account_id;
    ELSE
        v_activity_id := NEW.activity_id;
        v_owner_id := NEW.owner_user_account_id;
    END IF;

    SELECT activity_type, reverses_activity_id
      INTO v_activity_type, v_reverses_activity_id
      FROM ledger.activity
     WHERE owner_user_account_id = v_owner_id
       AND id = v_activity_id;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    IF v_activity_type IN ('SECURITY_BUY', 'SECURITY_SELL') THEN
        SELECT count(*), max(posting_role), max(gross_amount), max(trade_currency_code)
          INTO v_security_count, v_security_role, v_gross, v_security_currency
          FROM ledger.security_posting
         WHERE owner_user_account_id = v_owner_id
           AND activity_id = v_activity_id;
        SELECT financial_account_id, abs(quantity_delta), unit_price
          INTO v_security_account_id, v_quantity_abs, v_unit_price
          FROM ledger.security_posting
         WHERE owner_user_account_id = v_owner_id
           AND activity_id = v_activity_id;
        SELECT count(*) FILTER (WHERE posting_role = 'TRADE_PURCHASE'),
               count(*) FILTER (WHERE posting_role = 'TRADE_PROCEEDS'),
               count(*) FILTER (WHERE posting_role = 'FEE'),
               count(*),
               max(amount) FILTER (WHERE posting_role IN ('TRADE_PURCHASE', 'TRADE_PROCEEDS')),
               coalesce(sum(amount) FILTER (WHERE posting_role = 'FEE'), 0)
          INTO v_purchase_count, v_proceeds_count, v_fee_count, v_money_count, v_main_amount, v_fee_total
          FROM ledger.money_posting
         WHERE owner_user_account_id = v_owner_id
           AND activity_id = v_activity_id;

        IF v_security_count <> 1 OR v_money_count <> 1 + v_fee_count OR v_fee_count > 1 OR
                (v_activity_type = 'SECURITY_BUY' AND (v_security_role <> 'BUY' OR v_purchase_count <> 1 OR v_proceeds_count <> 0 OR
                        v_main_amount <> -v_gross)) OR
                (v_activity_type = 'SECURITY_SELL' AND (v_security_role <> 'SELL' OR v_purchase_count <> 0 OR v_proceeds_count <> 1 OR
                        v_main_amount <> v_gross OR v_gross + v_fee_total <= 0)) OR EXISTS (
                    SELECT 1
                      FROM ledger.money_posting cash_posting
                     WHERE cash_posting.owner_user_account_id = v_owner_id
                       AND cash_posting.activity_id = v_activity_id
                       AND (cash_posting.financial_account_id <> v_security_account_id OR cash_posting.currency_code <> v_security_currency)
                ) THEN
            RAISE EXCEPTION 'Trade activity cash and security postings do not reconcile' USING ERRCODE = '23514',
                CONSTRAINT = 'ck_ledger_trade_activity_posting_shape';
        END IF;

        SELECT minor_unit INTO v_minor_unit FROM reference.currency WHERE code = v_security_currency;
        v_raw_gross := v_quantity_abs * v_unit_price;
        v_currency_scale := power(10::numeric, v_minor_unit);
        v_scaled_gross := v_raw_gross * v_currency_scale;
        v_half_even_gross := (
            trunc(v_scaled_gross) + CASE
                WHEN v_scaled_gross - trunc(v_scaled_gross) > 0.5 THEN 1
                WHEN v_scaled_gross - trunc(v_scaled_gross) = 0.5 AND mod(trunc(v_scaled_gross), 2) = 1 THEN 1
                ELSE 0
            END
        ) / v_currency_scale;
        IF v_gross <> round(v_gross, v_minor_unit) OR v_gross <> v_half_even_gross OR EXISTS (
            SELECT 1
              FROM ledger.money_posting fee
             WHERE fee.owner_user_account_id = v_owner_id
               AND fee.activity_id = v_activity_id
               AND fee.posting_role = 'FEE'
               AND fee.amount <> round(fee.amount, v_minor_unit)
        ) THEN
            RAISE EXCEPTION 'Trade gross and commission must use currency minor-unit precision' USING ERRCODE = '23514',
                CONSTRAINT = 'ck_ledger_trade_activity_currency_precision';
        END IF;
    ELSIF v_activity_type = 'REVERSAL' AND EXISTS (
        SELECT 1
          FROM ledger.activity original_activity
         WHERE original_activity.owner_user_account_id = v_owner_id
           AND original_activity.id = v_reverses_activity_id
           AND original_activity.activity_type IN ('SECURITY_BUY', 'SECURITY_SELL')
    ) THEN
        SELECT count(*), max(posting_role)
          INTO v_security_count, v_security_role
          FROM ledger.security_posting
         WHERE owner_user_account_id = v_owner_id
           AND activity_id = v_activity_id;
        SELECT count(*)
          INTO v_original_money_count
          FROM ledger.money_posting original_posting
         WHERE original_posting.owner_user_account_id = v_owner_id
           AND original_posting.activity_id = v_reverses_activity_id
           AND original_posting.posting_role IN ('TRADE_PURCHASE', 'TRADE_PROCEEDS', 'FEE');
        SELECT count(*), count(*) FILTER (WHERE posting_role <> 'REVERSAL'),
               count(*) FILTER (WHERE reverses_money_posting_id IS NULL)
          INTO v_reversal_money_count, v_purchase_count, v_unlinked_reversal_count
          FROM ledger.money_posting
         WHERE owner_user_account_id = v_owner_id
           AND activity_id = v_activity_id;
        IF v_security_count <> 1 OR v_security_role <> 'REVERSAL' OR v_reversal_money_count <> v_original_money_count OR
                v_purchase_count <> 0 OR v_unlinked_reversal_count <> 0 OR EXISTS (
                    SELECT 1
                      FROM ledger.money_posting original_posting
                     WHERE original_posting.owner_user_account_id = v_owner_id
                       AND original_posting.activity_id = v_reverses_activity_id
                       AND original_posting.posting_role IN ('TRADE_PURCHASE', 'TRADE_PROCEEDS', 'FEE')
                       AND NOT EXISTS (
                           SELECT 1
                             FROM ledger.money_posting reversal_posting
                            WHERE reversal_posting.owner_user_account_id = v_owner_id
                              AND reversal_posting.activity_id = v_activity_id
                              AND reversal_posting.reverses_money_posting_id = original_posting.id
                       )
                ) THEN
            RAISE EXCEPTION 'Trade reversal must retain inverse cash and security links' USING ERRCODE = '23514',
                CONSTRAINT = 'ck_ledger_trade_reversal_posting_shape';
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_ledger_trade_activity_shape
    AFTER INSERT ON ledger.activity
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION ledger.validate_trade_activity_shape();

CREATE CONSTRAINT TRIGGER trg_ledger_trade_money_posting_shape
    AFTER INSERT ON ledger.money_posting
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION ledger.validate_trade_activity_shape();

CREATE CONSTRAINT TRIGGER trg_ledger_trade_security_posting_shape
    AFTER INSERT ON ledger.security_posting
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION ledger.validate_trade_activity_shape();

COMMENT ON TABLE ledger.security_posting IS 'Immutable signed native-currency security quantity and trade economics linked to ledger activities.';
COMMENT ON TABLE ledger.position_projection IS 'Synchronous weighted-average economic position projection rebuilt from immutable security postings.';
