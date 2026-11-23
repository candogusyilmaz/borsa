CREATE INDEX idx_transactions_action_date ON portfolio.transactions (action_date);
CREATE INDEX idx_portfolio_user_id ON portfolio.portfolios (user_id);
CREATE INDEX idx_positions_portfolio_id ON portfolio.positions (portfolio_id);
CREATE INDEX idx_transactions_position_id ON portfolio.transactions (position_id);

CREATE OR REPLACE FUNCTION public.convert_currency(
    p_amount DECIMAL(38, 18),
    p_source_currency VARCHAR(3),
    p_target_currency VARCHAR(3)
)
    RETURNS DECIMAL(38, 18) AS
$$
DECLARE
    v_source_rate DECIMAL(38, 18);
    v_target_rate DECIMAL(38, 18);
BEGIN
    IF p_source_currency = p_target_currency THEN
        RETURN p_amount;
    END IF;

    -- Get the exchange rates
    SELECT exchange_rate
    INTO v_source_rate
    FROM public.currencies
    WHERE code = p_source_currency;

    SELECT exchange_rate
    INTO v_target_rate
    FROM public.currencies
    WHERE code = p_target_currency;

    -- Validate that both currencies exist
    IF v_source_rate IS NULL THEN
        RAISE EXCEPTION 'Source currency % not found in the database', p_source_currency;
    END IF;

    IF v_target_rate IS NULL THEN
        RAISE EXCEPTION 'Target currency % not found in the database', p_target_currency;
    END IF;

    -- Convert amount from source currency to USD, then from USD to target currency
    RETURN (p_amount / v_source_rate * v_target_rate);
END;
$$ LANGUAGE plpgsql;