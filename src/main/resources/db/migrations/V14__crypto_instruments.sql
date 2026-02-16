CREATE TABLE instrument.crypto_instruments
(
    instrument_id BIGINT NOT NULL,
    CONSTRAINT pk_crypto_instruments PRIMARY KEY (instrument_id)
);

ALTER TABLE instrument.crypto_instruments
    ADD CONSTRAINT FK_CRYPTO_INSTRUMENTS_ON_INSTRUMENT FOREIGN KEY (instrument_id) REFERENCES instrument.instruments (id);

ALTER TABLE account.users
    ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE account.users
SET onboarding_completed = TRUE;
ALTER TABLE portfolio.portfolios
    ADD COLUMN color VARCHAR(7) NOT NULL DEFAULT '#3b82f6';