CREATE SCHEMA IF NOT EXISTS account;
CREATE SCHEMA IF NOT EXISTS portfolio;

-- Move user-related tables to accounts schema
ALTER TABLE public.users
    SET SCHEMA account;
ALTER TABLE public.user_roles
    SET SCHEMA account;
ALTER TABLE public.permissions
    SET SCHEMA account;
ALTER TABLE public.roles
    SET SCHEMA account;
ALTER TABLE public.role_permissions
    SET SCHEMA account;

-- Move portfolio tables to portfolio schema
ALTER TABLE public.holdings
    SET SCHEMA portfolio;
ALTER TABLE public.holding_daily_snapshots
    SET SCHEMA portfolio;
ALTER TABLE public.holding_history
    SET SCHEMA portfolio;
ALTER TABLE public.portfolios
    SET SCHEMA portfolio;
ALTER TABLE public.trades
    SET SCHEMA portfolio;
ALTER TABLE public.trade_performance
    SET SCHEMA portfolio;

-- Rename holdings and related tables to positions
ALTER TABLE portfolio.holdings
    RENAME TO positions;
ALTER TABLE portfolio.holding_daily_snapshots
    RENAME TO position_daily_snapshots;
ALTER TABLE portfolio.holding_history
    RENAME TO position_history;
ALTER TABLE portfolio.position_daily_snapshots
    RENAME COLUMN holding_id TO position_id;
ALTER TABLE portfolio.position_history
    RENAME COLUMN holding_id TO position_id;

-- Rename trades and related tables to transactions
ALTER TABLE portfolio.trades
    RENAME TO transactions;
ALTER TABLE portfolio.trade_performance
    RENAME TO transaction_performance;
ALTER TABLE portfolio.transaction_performance
    RENAME COLUMN trade_id TO transaction_id;
ALTER TABLE portfolio.transactions
    RENAME COLUMN holding_id to position_id;