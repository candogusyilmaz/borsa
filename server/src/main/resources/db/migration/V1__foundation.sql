-- V1 Foundation: application schemas and the five foundation tables for identity and platform.
-- DDL only: no data, no extensions, no triggers, no views, no functions, no sequences.
-- Application code generates all UUIDs; no DB-default UUID strategy is introduced.
-- =====================================================================
-- Schemas
-- =====================================================================
CREATE SCHEMA identity;
CREATE SCHEMA reference;
CREATE SCHEMA ledger;
CREATE SCHEMA data;
CREATE SCHEMA money;
CREATE SCHEMA analysis;
CREATE SCHEMA asset;
CREATE SCHEMA platform;
-- =====================================================================
-- identity.user_account
-- Stable application user identity independent of authentication provider.
-- email_normalized is the canonical lowercase lookup key; email retains
-- the user-supplied casing for display. Both must have no leading/trailing
-- whitespace; application code owns the normalization logic.
-- =====================================================================
CREATE TABLE identity.user_account (
    id uuid NOT NULL,
    email text NOT NULL,
    email_normalized text NOT NULL,
    disabled_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_account PRIMARY KEY (id),
    CONSTRAINT uq_user_account_email_normalized UNIQUE (email_normalized),
    -- no leading/trailing whitespace and non-blank
    CONSTRAINT ck_user_account_email_not_blank CHECK (
        length(trim(email)) > 0
        AND email = trim(email)
    ),
    -- lowercase, no leading/trailing whitespace, and non-blank
    CONSTRAINT ck_user_account_email_normalized_valid CHECK (
        length(trim(email_normalized)) > 0
        AND email_normalized = trim(email_normalized)
        AND email_normalized = lower(email_normalized)
    )
);
-- =====================================================================
-- identity.auth_identity
-- Binds a user to one authentication-provider identity.
-- Supports local auth (provider = 'LOCAL') and future external providers
-- without schema changes. Provider codes are application values; no DB
-- enum limits them here.
-- =====================================================================
CREATE TABLE identity.auth_identity (
    id uuid NOT NULL,
    user_account_id uuid NOT NULL,
    provider text NOT NULL,
    provider_subject text NOT NULL,
    password_hash text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_auth_identity PRIMARY KEY (id),
    CONSTRAINT fk_auth_identity_user_account FOREIGN KEY (user_account_id) REFERENCES identity.user_account (id) ON DELETE CASCADE,
    -- globally unique provider identity
    CONSTRAINT uq_auth_identity_provider_subject UNIQUE (provider, provider_subject),
    -- at most one identity per provider per user
    CONSTRAINT uq_auth_identity_user_provider UNIQUE (user_account_id, provider),
    CONSTRAINT ck_auth_identity_provider_not_blank CHECK (
        length(trim(provider)) > 0
        AND provider = trim(provider)
    ),
    CONSTRAINT ck_auth_identity_provider_subject_not_blank CHECK (
        length(trim(provider_subject)) > 0
        AND provider_subject = trim(provider_subject)
    )
);
-- uq_auth_identity_user_provider already provides an index on user_account_id
-- as the leading column; no separate single-column index is needed.
-- =====================================================================
-- identity.device_session
-- Append-only refresh-token rotation history.
-- One row = one refresh-token generation. Token rotation creates a new row
-- in the same family_id and revokes/links the prior row. Raw tokens are
-- never stored; only a one-way hash is persisted.
-- =====================================================================
CREATE TABLE identity.device_session (
    id uuid NOT NULL,
    user_account_id uuid NOT NULL,
    family_id uuid NOT NULL,
    refresh_token_hash text NOT NULL,
    device_label text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at timestamptz,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    revoke_reason text,
    replaced_by_session_id uuid,
    CONSTRAINT pk_device_session PRIMARY KEY (id),
    CONSTRAINT fk_device_session_user_account FOREIGN KEY (user_account_id) REFERENCES identity.user_account (id) ON DELETE CASCADE,
    -- SET NULL so deleting a session does not cascade-delete its successor
    CONSTRAINT fk_device_session_replaced_by FOREIGN KEY (replaced_by_session_id) REFERENCES identity.device_session (id) ON DELETE
    SET NULL,
        CONSTRAINT uq_device_session_refresh_token_hash UNIQUE (refresh_token_hash),
        CONSTRAINT ck_device_session_expires_after_created CHECK (expires_at > created_at),
        CONSTRAINT ck_device_session_no_self_replacement CHECK (
            replaced_by_session_id IS NULL
            OR replaced_by_session_id <> id
        )
);
-- efficient listing of all sessions belonging to a user
CREATE INDEX ix_device_session_user_account_id ON identity.device_session (user_account_id);
-- efficient family-chain lookup during token rotation
CREATE INDEX ix_device_session_family_id ON identity.device_session (family_id);
-- rotation invariant: at most one non-revoked row may exist per family at a time
CREATE UNIQUE INDEX uix_device_session_active_family ON identity.device_session (family_id)
WHERE revoked_at IS NULL;
-- =====================================================================
-- platform.security_event
-- Append-only log of security-relevant events.
-- Anonymous events (e.g. failed login attempts) have user_account_id = null.
-- details is sparse event metadata; JSONB is appropriate here.
-- =====================================================================
CREATE TABLE platform.security_event (
    id uuid NOT NULL,
    user_account_id uuid,
    event_type text NOT NULL,
    occurred_at timestamptz NOT NULL,
    details jsonb NOT NULL DEFAULT '{}',
    CONSTRAINT pk_security_event PRIMARY KEY (id),
    -- nullable FK: anonymous events are valid; user-scoped events are deleted with the user
    CONSTRAINT fk_security_event_user_account FOREIGN KEY (user_account_id) REFERENCES identity.user_account (id) ON DELETE CASCADE,
    CONSTRAINT ck_security_event_event_type_not_blank CHECK (
        length(trim(event_type)) > 0
        AND event_type = trim(event_type)
    ),
    -- prevent accidental storage of a scalar/array where structured data is expected
    CONSTRAINT ck_security_event_details_is_object CHECK (jsonb_typeof(details) = 'object')
);
-- user-scoped event history in reverse-chronological order
CREATE INDEX ix_security_event_user_occurred ON platform.security_event (user_account_id, occurred_at DESC);
-- security investigation/query by event type in reverse-chronological order
CREATE INDEX ix_security_event_type_occurred ON platform.security_event (event_type, occurred_at DESC);
-- =====================================================================
-- platform.job
-- Durable job/queue state table for later imports and background rebuilds.
-- This PR creates storage only. Worker claim and retry behavior (FOR UPDATE
-- SKIP LOCKED) are implemented in PR-004.
-- =====================================================================
CREATE TABLE platform.job (
    id uuid NOT NULL,
    owner_user_account_id uuid,
    job_type text NOT NULL,
    status text NOT NULL DEFAULT 'READY',
    payload jsonb NOT NULL DEFAULT '{}',
    available_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_by text,
    claim_token uuid,
    claimed_at timestamptz,
    heartbeat_at timestamptz,
    attempt_count integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 5,
    completed_at timestamptz,
    last_error text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_job PRIMARY KEY (id),
    -- nullable FK: null = system job; non-null = user-scoped job deleted with the user
    CONSTRAINT fk_job_owner_user_account FOREIGN KEY (owner_user_account_id) REFERENCES identity.user_account (id) ON DELETE CASCADE,
    -- migration-owned status machine; application codes not a PostgreSQL enum
    CONSTRAINT ck_job_status_valid CHECK (
        status IN (
            'READY',
            'RUNNING',
            'SUCCEEDED',
            'FAILED',
            'CANCELLED'
        )
    ),
    CONSTRAINT ck_job_type_not_blank CHECK (
        length(trim(job_type)) > 0
        AND job_type = trim(job_type)
    ),
    CONSTRAINT ck_job_payload_is_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_job_attempt_count_non_negative CHECK (attempt_count >= 0),
    CONSTRAINT ck_job_max_attempts_positive CHECK (max_attempts > 0),
    CONSTRAINT ck_job_attempt_count_within_max CHECK (attempt_count <= max_attempts),
    -- a claimed job must carry full ownership metadata
    CONSTRAINT ck_job_running_requires_claim_metadata CHECK (
        status <> 'RUNNING'
        OR (
            claimed_by IS NOT NULL
            AND claim_token IS NOT NULL
            AND claimed_at IS NOT NULL
        )
    ),
    -- terminal transitions must record when they completed
    CONSTRAINT ck_job_terminal_requires_completed_at CHECK (
        status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED')
        OR completed_at IS NOT NULL
    )
);
-- claim polling: ready jobs ordered by when they become available, then insertion order
CREATE INDEX ix_job_claim ON platform.job (available_at, created_at)
WHERE status = 'READY';
-- heartbeat / stale-running-job recovery
CREATE INDEX ix_job_recovery ON platform.job (heartbeat_at)
WHERE status = 'RUNNING';