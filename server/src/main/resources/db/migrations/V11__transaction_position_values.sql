ALTER TABLE portfolio.transactions
    ADD COLUMN new_quantity NUMERIC(38, 18) NOT NULL DEFAULT 1,
    ADD COLUMN new_total    NUMERIC(38, 18) NOT NULL DEFAULT 1;

CREATE TYPE tag_type AS ENUM ('TRANSACTION');
CREATE TABLE tags
(
    id       bigint PRIMARY KEY,
    type     tag_type     NOT NULL,
    lang_key VARCHAR(100) NOT NULL,
    name     VARCHAR(100) NOT NULL,
    UNIQUE (type, name)
);