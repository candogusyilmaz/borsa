CREATE TABLE ledger.portfolio (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    name text NOT NULL,
    name_normalized text NOT NULL,
    archived_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_ledger_portfolio PRIMARY KEY (id),
    CONSTRAINT fk_ledger_portfolio_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT uq_ledger_portfolio_owner_id UNIQUE (owner_user_account_id, id),
    CONSTRAINT ck_ledger_portfolio_name CHECK (
        name = btrim(name)
        AND char_length(name) BETWEEN 1 AND 160
    ),
    CONSTRAINT ck_ledger_portfolio_name_normalized CHECK (
        name_normalized = upper(name)
        AND char_length(name_normalized) BETWEEN 1 AND 160
    ),
    CONSTRAINT ck_ledger_portfolio_version_non_negative CHECK (version >= 0)
);

CREATE UNIQUE INDEX uix_ledger_portfolio_active_name
    ON ledger.portfolio (owner_user_account_id, name_normalized)
    WHERE archived_at IS NULL;

CREATE INDEX ix_ledger_portfolio_owner_name
    ON ledger.portfolio (owner_user_account_id, name_normalized, id);

CREATE TABLE ledger.portfolio_account_membership (
    id uuid NOT NULL,
    owner_user_account_id uuid NOT NULL,
    portfolio_id uuid NOT NULL,
    financial_account_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_ledger_portfolio_account_membership PRIMARY KEY (id),
    CONSTRAINT fk_ledger_portfolio_account_membership_owner FOREIGN KEY (owner_user_account_id)
        REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_portfolio_account_membership_portfolio FOREIGN KEY (owner_user_account_id, portfolio_id)
        REFERENCES ledger.portfolio (owner_user_account_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_portfolio_account_membership_account FOREIGN KEY (owner_user_account_id, financial_account_id)
        REFERENCES ledger.financial_account (owner_user_account_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_ledger_portfolio_account_membership UNIQUE (owner_user_account_id, portfolio_id, financial_account_id)
);

CREATE INDEX ix_ledger_portfolio_account_membership_account
    ON ledger.portfolio_account_membership (owner_user_account_id, financial_account_id, portfolio_id);
