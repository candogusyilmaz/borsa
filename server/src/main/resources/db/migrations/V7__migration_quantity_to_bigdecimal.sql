ALTER TABLE portfolio.positions
    ALTER COLUMN quantity TYPE numeric(38, 18) USING quantity::numeric(38, 18);

ALTER TABLE portfolio.transactions
    ALTER COLUMN quantity TYPE numeric(38, 18) USING quantity::numeric(38, 18),
    ALTER COLUMN price TYPE numeric(38, 18) USING price::numeric(38, 18),
    ALTER COLUMN tax TYPE numeric(38, 18) USING tax::numeric(38, 18);

ALTER TABLE portfolio.transaction_performance
    ALTER COLUMN profit TYPE numeric(38, 18) USING profit::numeric(38, 18),
    ALTER COLUMN return_percentage TYPE numeric(38, 18) USING return_percentage::numeric(38, 18);

ALTER TABLE portfolio.position_history
    ALTER COLUMN quantity TYPE numeric(38, 18) USING quantity::numeric(38, 18),
    ALTER COLUMN total TYPE numeric(38, 18) USING total::numeric(38, 18);

ALTER TABLE portfolio.transactions
    RENAME COLUMN tax TO commission;
