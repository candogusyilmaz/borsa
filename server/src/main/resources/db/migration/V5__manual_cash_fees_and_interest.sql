-- V5 explicit manual cash fee and interest-credit activities.

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
            activity_type IN ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'CASH_FEE', 'CASH_INTEREST_CREDIT', 'OWNED_TRANSFER')
            AND recording_mode = 'CURRENT_ACTION'
            AND policy_decision IN ('ALLOWED', 'CONFIRMED_BREACH', 'HISTORICAL_BREACH_RECORDED')
        )
        OR (
            activity_type IN ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'CASH_FEE', 'CASH_INTEREST_CREDIT', 'OWNED_TRANSFER')
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
        OR (posting_role IN ('DEPOSIT', 'TRANSFER_DESTINATION', 'INTEREST_CREDIT') AND amount > 0)
        OR (posting_role IN ('WITHDRAWAL', 'TRANSFER_SOURCE', 'FEE') AND amount < 0)
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
            'REVERSAL',
            'ADJUSTMENT'
        )
    );
