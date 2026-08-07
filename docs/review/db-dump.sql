--
-- PostgreSQL database dump
--
-- Dumped from database version 15.1 (Debian 15.1-1.pgdg110+1)
-- Dumped by pg_dump version 15.1 (Debian 15.1-1.pgdg110+1)
SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;
ALTER TABLE IF EXISTS ONLY public.batch_step_execution_context DROP CONSTRAINT IF EXISTS step_exec_ctx_fk;
ALTER TABLE IF EXISTS ONLY public.batch_job_execution DROP CONSTRAINT IF EXISTS job_inst_exec_fk;
ALTER TABLE IF EXISTS ONLY public.batch_step_execution DROP CONSTRAINT IF EXISTS job_exec_step_fk;
ALTER TABLE IF EXISTS ONLY public.batch_job_execution_params DROP CONSTRAINT IF EXISTS job_exec_params_fk;
ALTER TABLE IF EXISTS ONLY public.batch_job_execution_context DROP CONSTRAINT IF EXISTS job_exec_ctx_fk;
ALTER TABLE IF EXISTS ONLY portfolio.transactions DROP CONSTRAINT IF EXISTS fkltmwmanjxv15jtw01wnyiobvn;
ALTER TABLE IF EXISTS ONLY portfolio.positions DROP CONSTRAINT IF EXISTS fk_positions_instrument;
ALTER TABLE IF EXISTS ONLY portfolio.portfolios DROP CONSTRAINT IF EXISTS fk_portfolios_on_user;
ALTER TABLE IF EXISTS ONLY portfolio.positions DROP CONSTRAINT IF EXISTS fk_holdings_on_portfolio;
ALTER TABLE IF EXISTS ONLY portfolio.dashboards DROP CONSTRAINT IF EXISTS fk_dashboards_on_user;
ALTER TABLE IF EXISTS ONLY portfolio.dashboards DROP CONSTRAINT IF EXISTS fk_dashboards_on_currency;
ALTER TABLE IF EXISTS ONLY portfolio.dashboard_portfolios DROP CONSTRAINT IF EXISTS fk_dashboard_portfolios_on_portfolio;
ALTER TABLE IF EXISTS ONLY portfolio.dashboard_portfolios DROP CONSTRAINT IF EXISTS fk_dashboard_portfolios_on_dashboard;
ALTER TABLE IF EXISTS ONLY portfolio.transaction_performance DROP CONSTRAINT IF EXISTS fk9iwei2dph6ak0hd78adxh1ai5;
ALTER TABLE IF EXISTS ONLY portfolio.position_daily_snapshots DROP CONSTRAINT IF EXISTS fk4mq514o9flf0iayvg64cm2h9r;
ALTER TABLE IF EXISTS ONLY portfolio.position_history DROP CONSTRAINT IF EXISTS fk49rq4lvgdn71eui7nt3xfg8yl;
ALTER TABLE IF EXISTS ONLY instrument.market_currencies DROP CONSTRAINT IF EXISTS market_currencies_market_id_fkey;
ALTER TABLE IF EXISTS ONLY instrument.stock_instruments DROP CONSTRAINT IF EXISTS fk_stock_instruments_on_instrument;
ALTER TABLE IF EXISTS ONLY instrument.markets DROP CONSTRAINT IF EXISTS fk_markets_on_country;
ALTER TABLE IF EXISTS ONLY instrument.instruments DROP CONSTRAINT IF EXISTS fk_instruments_on_market;
ALTER TABLE IF EXISTS ONLY instrument.instrument_snapshots DROP CONSTRAINT IF EXISTS fk_instrument_snapshots_on_instrument;
ALTER TABLE IF EXISTS ONLY instrument.crypto_instruments DROP CONSTRAINT IF EXISTS fk_crypto_instruments_on_instrument;
ALTER TABLE IF EXISTS ONLY account.role_permissions DROP CONSTRAINT IF EXISTS fkn5fotdgk8d1xvo8nav9uv3muc;
ALTER TABLE IF EXISTS ONLY account.user_roles DROP CONSTRAINT IF EXISTS fkhfh9dx7w3ubf1co1vdev94g3f;
ALTER TABLE IF EXISTS ONLY account.user_roles DROP CONSTRAINT IF EXISTS fkh8ciramu9cc9q3qcqiv4ue8a6;
ALTER TABLE IF EXISTS ONLY account.role_permissions DROP CONSTRAINT IF EXISTS fkegdk29eiy7mdtefy5c7eirr6e;
DROP INDEX IF EXISTS public.flyway_schema_history_s_idx;
DROP INDEX IF EXISTS portfolio.ux_dashboards_user_id_is_default_true;
DROP INDEX IF EXISTS portfolio.idx_transactions_position_id;
DROP INDEX IF EXISTS portfolio.idx_transactions_action_date;
DROP INDEX IF EXISTS portfolio.idx_positions_portfolio_id;
DROP INDEX IF EXISTS portfolio.idx_portfolios_user_id;
DROP INDEX IF EXISTS portfolio.idx_portfolio_user_id;
DROP INDEX IF EXISTS portfolio.idx_holdings_portfolio_id;
DROP INDEX IF EXISTS instrument.idx_instruments_type;
DROP INDEX IF EXISTS instrument.idx_instruments_symbol;
DROP INDEX IF EXISTS instrument.idx_instruments_market;
ALTER TABLE IF EXISTS ONLY public.countries DROP CONSTRAINT IF EXISTS uk20ieiirrqjrlkw677k8fq6soj;
ALTER TABLE IF EXISTS ONLY public.countries DROP CONSTRAINT IF EXISTS uk1pyiwrqimi3hnl3vtgsypj5r;
ALTER TABLE IF EXISTS ONLY public.currencies DROP CONSTRAINT IF EXISTS uc_currencies_code;
ALTER TABLE IF EXISTS ONLY public.currencies DROP CONSTRAINT IF EXISTS pk_currencies;
ALTER TABLE IF EXISTS ONLY public.batch_job_instance DROP CONSTRAINT IF EXISTS job_inst_un;
ALTER TABLE IF EXISTS ONLY public.flyway_schema_history DROP CONSTRAINT IF EXISTS flyway_schema_history_pk;
ALTER TABLE IF EXISTS ONLY public.countries DROP CONSTRAINT IF EXISTS countries_pkey;
ALTER TABLE IF EXISTS ONLY public.batch_step_execution DROP CONSTRAINT IF EXISTS batch_step_execution_pkey;
ALTER TABLE IF EXISTS ONLY public.batch_step_execution_context DROP CONSTRAINT IF EXISTS batch_step_execution_context_pkey;
ALTER TABLE IF EXISTS ONLY public.batch_job_instance DROP CONSTRAINT IF EXISTS batch_job_instance_pkey;
ALTER TABLE IF EXISTS ONLY public.batch_job_execution DROP CONSTRAINT IF EXISTS batch_job_execution_pkey;
ALTER TABLE IF EXISTS ONLY public.batch_job_execution_context DROP CONSTRAINT IF EXISTS batch_job_execution_context_pkey;
ALTER TABLE IF EXISTS ONLY portfolio.dashboard_portfolios DROP CONSTRAINT IF EXISTS ux_dashboard_portfolios_on_dashboard_id_and_portfolio_id;
ALTER TABLE IF EXISTS ONLY portfolio.transactions DROP CONSTRAINT IF EXISTS trades_pkey;
ALTER TABLE IF EXISTS ONLY portfolio.transaction_performance DROP CONSTRAINT IF EXISTS trade_performance_pkey;
ALTER TABLE IF EXISTS ONLY portfolio.portfolios DROP CONSTRAINT IF EXISTS pk_portfolios;
ALTER TABLE IF EXISTS ONLY portfolio.dashboards DROP CONSTRAINT IF EXISTS pk_dashboards;
ALTER TABLE IF EXISTS ONLY portfolio.dashboard_portfolios DROP CONSTRAINT IF EXISTS pk_dashboard_portfolios;
ALTER TABLE IF EXISTS ONLY portfolio.positions DROP CONSTRAINT IF EXISTS holdings_pkey;
ALTER TABLE IF EXISTS ONLY portfolio.position_history DROP CONSTRAINT IF EXISTS holding_history_pkey;
ALTER TABLE IF EXISTS ONLY portfolio.position_daily_snapshots DROP CONSTRAINT IF EXISTS holding_daily_snapshots_pkey;
ALTER TABLE IF EXISTS ONLY instrument.instrument_snapshots DROP CONSTRAINT IF EXISTS uq_instrument_currency;
ALTER TABLE IF EXISTS ONLY instrument.markets DROP CONSTRAINT IF EXISTS uc_markets_name;
ALTER TABLE IF EXISTS ONLY instrument.instruments DROP CONSTRAINT IF EXISTS uc_instruments_symbol;
ALTER TABLE IF EXISTS ONLY instrument.stock_instruments DROP CONSTRAINT IF EXISTS pk_stock_instruments;
ALTER TABLE IF EXISTS ONLY instrument.markets DROP CONSTRAINT IF EXISTS pk_markets;
ALTER TABLE IF EXISTS ONLY instrument.instruments DROP CONSTRAINT IF EXISTS pk_instruments;
ALTER TABLE IF EXISTS ONLY instrument.instrument_snapshots DROP CONSTRAINT IF EXISTS pk_instrument_snapshots;
ALTER TABLE IF EXISTS ONLY instrument.crypto_instruments DROP CONSTRAINT IF EXISTS pk_crypto_instruments;
ALTER TABLE IF EXISTS ONLY instrument.market_currencies DROP CONSTRAINT IF EXISTS market_currencies_pkey;
ALTER TABLE IF EXISTS ONLY account.users DROP CONSTRAINT IF EXISTS users_pkey;
ALTER TABLE IF EXISTS ONLY account.user_roles DROP CONSTRAINT IF EXISTS user_roles_pkey;
ALTER TABLE IF EXISTS ONLY account.permissions DROP CONSTRAINT IF EXISTS ukpnvtwliis6p05pn6i3ndjrqt2;
ALTER TABLE IF EXISTS ONLY account.roles DROP CONSTRAINT IF EXISTS ukofx66keruapi6vyqpv6f2or37;
ALTER TABLE IF EXISTS ONLY account.users DROP CONSTRAINT IF EXISTS uk6dotkott2kjsp8vw4d0m25fb7;
ALTER TABLE IF EXISTS ONLY account.roles DROP CONSTRAINT IF EXISTS roles_pkey;
ALTER TABLE IF EXISTS ONLY account.role_permissions DROP CONSTRAINT IF EXISTS role_permissions_pkey;
ALTER TABLE IF EXISTS ONLY account.permissions DROP CONSTRAINT IF EXISTS permissions_pkey;
DROP TABLE IF EXISTS public.flyway_schema_history;
DROP TABLE IF EXISTS public.currencies;
DROP TABLE IF EXISTS public.countries;
DROP SEQUENCE IF EXISTS public.batch_step_execution_seq;
DROP TABLE IF EXISTS public.batch_step_execution_context;
DROP TABLE IF EXISTS public.batch_step_execution;
DROP SEQUENCE IF EXISTS public.batch_job_seq;
DROP TABLE IF EXISTS public.batch_job_instance;
DROP SEQUENCE IF EXISTS public.batch_job_execution_seq;
DROP TABLE IF EXISTS public.batch_job_execution_params;
DROP TABLE IF EXISTS public.batch_job_execution_context;
DROP TABLE IF EXISTS public.batch_job_execution;
DROP TABLE IF EXISTS portfolio.transaction_performance;
DROP TABLE IF EXISTS portfolio.transactions;
DROP TABLE IF EXISTS portfolio.portfolios;
DROP TABLE IF EXISTS portfolio.positions;
DROP TABLE IF EXISTS portfolio.position_history;
DROP TABLE IF EXISTS portfolio.position_daily_snapshots;
DROP TABLE IF EXISTS portfolio.dashboards;
DROP TABLE IF EXISTS portfolio.dashboard_portfolios;
DROP TABLE IF EXISTS instrument.stock_instruments;
DROP TABLE IF EXISTS instrument.markets;
DROP TABLE IF EXISTS instrument.market_currencies;
DROP TABLE IF EXISTS instrument.instruments;
DROP TABLE IF EXISTS instrument.instrument_snapshots;
DROP TABLE IF EXISTS instrument.crypto_instruments;
DROP TABLE IF EXISTS account.users;
DROP TABLE IF EXISTS account.user_roles;
DROP TABLE IF EXISTS account.roles;
DROP TABLE IF EXISTS account.role_permissions;
DROP TABLE IF EXISTS account.permissions;
DROP FUNCTION IF EXISTS public.convert_currency(
    p_amount numeric,
    p_source_currency character varying,
    p_target_currency character varying
);
DROP TYPE IF EXISTS public.tag_type;
DROP TYPE IF EXISTS public.market_type;
DROP TYPE IF EXISTS public.instrument_type;
DROP SCHEMA IF EXISTS portfolio;
DROP SCHEMA IF EXISTS instrument;
DROP SCHEMA IF EXISTS account;
--
-- Name: account; Type: SCHEMA; Schema: -; Owner: -
--
CREATE SCHEMA account;
--
-- Name: instrument; Type: SCHEMA; Schema: -; Owner: -
--
CREATE SCHEMA instrument;
--
-- Name: portfolio; Type: SCHEMA; Schema: -; Owner: -
--
CREATE SCHEMA portfolio;
--
-- Name: instrument_type; Type: TYPE; Schema: public; Owner: -
--
CREATE TYPE public.instrument_type AS ENUM (
    'STOCK',
    'CRYPTOCURRENCY',
    'CURRENCY_PAIR',
    'COMMODITY',
    'INDEX'
);
--
-- Name: market_type; Type: TYPE; Schema: public; Owner: -
--
CREATE TYPE public.market_type AS ENUM (
    'STOCK_EXCHANGE',
    'CRYPTOCURRENCY',
    'FOREX',
    'COMMODITY',
    'INDEX'
);
--
-- Name: tag_type; Type: TYPE; Schema: public; Owner: -
--
CREATE TYPE public.tag_type AS ENUM ('TRANSACTION');
--
-- Name: convert_currency(numeric, character varying, character varying); Type: FUNCTION; Schema: public; Owner: -
--
CREATE FUNCTION public.convert_currency(
    p_amount numeric,
    p_source_currency character varying,
    p_target_currency character varying
) RETURNS numeric LANGUAGE plpgsql AS $$
DECLARE v_source_rate DECIMAL(38, 18);
v_target_rate DECIMAL(38, 18);
BEGIN IF p_source_currency = p_target_currency THEN RETURN p_amount;
END IF;
-- Get the exchange rates
SELECT exchange_rate INTO v_source_rate
FROM public.currencies
WHERE code = p_source_currency;
SELECT exchange_rate INTO v_target_rate
FROM public.currencies
WHERE code = p_target_currency;
-- Validate that both currencies exist
IF v_source_rate IS NULL THEN RAISE EXCEPTION 'Source currency % not found in the database',
p_source_currency;
END IF;
IF v_target_rate IS NULL THEN RAISE EXCEPTION 'Target currency % not found in the database',
p_target_currency;
END IF;
-- Convert amount from source currency to USD, then from USD to target currency
RETURN (p_amount / v_source_rate * v_target_rate);
END;
$$;
SET default_tablespace = '';
SET default_table_access_method = heap;
--
-- Name: permissions; Type: TABLE; Schema: account; Owner: -
--
CREATE TABLE account.permissions (
    id bigint NOT NULL,
    display_name character varying(255) NOT NULL,
    name character varying(255) NOT NULL
);
--
-- Name: permissions_id_seq; Type: SEQUENCE; Schema: account; Owner: -
--
ALTER TABLE account.permissions
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME account.permissions_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: role_permissions; Type: TABLE; Schema: account; Owner: -
--
CREATE TABLE account.role_permissions (
    id bigint NOT NULL,
    permission_id bigint NOT NULL,
    role_id bigint NOT NULL
);
--
-- Name: role_permissions_id_seq; Type: SEQUENCE; Schema: account; Owner: -
--
ALTER TABLE account.role_permissions
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME account.role_permissions_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: roles; Type: TABLE; Schema: account; Owner: -
--
CREATE TABLE account.roles (
    id bigint NOT NULL,
    level integer NOT NULL,
    name character varying(255) NOT NULL
);
--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: account; Owner: -
--
ALTER TABLE account.roles
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME account.roles_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: user_roles; Type: TABLE; Schema: account; Owner: -
--
CREATE TABLE account.user_roles (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    user_id bigint NOT NULL
);
--
-- Name: user_roles_id_seq; Type: SEQUENCE; Schema: account; Owner: -
--
ALTER TABLE account.user_roles
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME account.user_roles_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: users; Type: TABLE; Schema: account; Owner: -
--
CREATE TABLE account.users (
    id bigint NOT NULL,
    email character varying(255),
    is_enabled boolean NOT NULL,
    password character varying(255) NOT NULL,
    name character varying(255),
    last_login_at timestamp without time zone DEFAULT now() NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    onboarding_completed boolean DEFAULT false NOT NULL
);
--
-- Name: users_id_seq; Type: SEQUENCE; Schema: account; Owner: -
--
ALTER TABLE account.users
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME account.users_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: crypto_instruments; Type: TABLE; Schema: instrument; Owner: -
--
CREATE TABLE instrument.crypto_instruments (instrument_id bigint NOT NULL);
--
-- Name: instrument_snapshots; Type: TABLE; Schema: instrument; Owner: -
--
CREATE TABLE instrument.instrument_snapshots (
    instrument_id bigint NOT NULL,
    last numeric(38, 18) NOT NULL,
    previous_close numeric(38, 18),
    daily_change numeric(38, 18),
    daily_change_percent numeric(38, 18),
    updated_at timestamp without time zone NOT NULL,
    id bigint NOT NULL,
    currency_code character varying(3) NOT NULL
);
--
-- Name: instrument_snapshots_id_seq; Type: SEQUENCE; Schema: instrument; Owner: -
--
ALTER TABLE instrument.instrument_snapshots
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME instrument.instrument_snapshots_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: instruments; Type: TABLE; Schema: instrument; Owner: -
--
CREATE TABLE instrument.instruments (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    symbol character varying(255) NOT NULL,
    type public.instrument_type NOT NULL,
    market_id bigint NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);
--
-- Name: instruments_id_seq; Type: SEQUENCE; Schema: instrument; Owner: -
--
ALTER TABLE instrument.instruments
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME instrument.instruments_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: market_currencies; Type: TABLE; Schema: instrument; Owner: -
--
CREATE TABLE instrument.market_currencies (
    market_id bigint NOT NULL,
    currency_code character varying(3) NOT NULL
);
--
-- Name: markets; Type: TABLE; Schema: instrument; Owner: -
--
CREATE TABLE instrument.markets (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(255) NOT NULL,
    type public.market_type NOT NULL,
    country_id bigint,
    timezone character varying(255) NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);
--
-- Name: markets_id_seq; Type: SEQUENCE; Schema: instrument; Owner: -
--
ALTER TABLE instrument.markets
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME instrument.markets_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: stock_instruments; Type: TABLE; Schema: instrument; Owner: -
--
CREATE TABLE instrument.stock_instruments (
    instrument_id bigint NOT NULL,
    isin character varying(12)
);
--
-- Name: dashboard_portfolios; Type: TABLE; Schema: portfolio; Owner: -
--
CREATE TABLE portfolio.dashboard_portfolios (
    id bigint NOT NULL,
    dashboard_id bigint NOT NULL,
    portfolio_id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL
);
--
-- Name: dashboard_portfolios_id_seq; Type: SEQUENCE; Schema: portfolio; Owner: -
--
ALTER TABLE portfolio.dashboard_portfolios
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME portfolio.dashboard_portfolios_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: dashboards; Type: TABLE; Schema: portfolio; Owner: -
--
CREATE TABLE portfolio.dashboards (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    currency_id bigint NOT NULL,
    is_default boolean NOT NULL,
    user_id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);
--
-- Name: dashboards_id_seq; Type: SEQUENCE; Schema: portfolio; Owner: -
--
ALTER TABLE portfolio.dashboards
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME portfolio.dashboards_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: position_daily_snapshots; Type: TABLE; Schema: portfolio; Owner: -
--
CREATE TABLE portfolio.position_daily_snapshots (
    id bigint NOT NULL,
    average_price numeric(20, 8) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    daily_profit numeric(15, 2) GENERATED ALWAYS AS (
        (
            ((quantity)::numeric * market_price) - ((quantity)::numeric * previous_market_price)
        )
    ) STORED NOT NULL,
    daily_profit_percentage numeric(5, 2) GENERATED ALWAYS AS (
        CASE
            WHEN (previous_market_price > (0)::numeric) THEN (
                (
                    (
                        ((quantity)::numeric * market_price) - ((quantity)::numeric * previous_market_price)
                    ) / ((quantity)::numeric * previous_market_price)
                ) * (100)::numeric
            )
            ELSE NULL::numeric
        END
    ) STORED NOT NULL,
    market_price numeric(20, 8) NOT NULL,
    market_value numeric(15, 2) GENERATED ALWAYS AS (((quantity)::numeric * market_price)) STORED NOT NULL,
    portfolio_weight_percentage numeric(5, 2) NOT NULL,
    previous_market_price numeric(20, 8) NOT NULL,
    quantity integer NOT NULL,
    total_commission numeric(20, 8),
    total_cost numeric(15, 2) GENERATED ALWAYS AS (((quantity)::numeric * average_price)) STORED NOT NULL,
    total_profit numeric(15, 2) GENERATED ALWAYS AS (
        (
            ((quantity)::numeric * market_price) - ((quantity)::numeric * average_price)
        )
    ) STORED NOT NULL,
    total_profit_percentage numeric(5, 2) GENERATED ALWAYS AS (
        CASE
            WHEN (
                ((quantity)::numeric * average_price) > (0)::numeric
            ) THEN (
                (
                    (
                        ((quantity)::numeric * market_price) - ((quantity)::numeric * average_price)
                    ) / ((quantity)::numeric * average_price)
                ) * (100)::numeric
            )
            ELSE NULL::numeric
        END
    ) STORED NOT NULL,
    position_id bigint NOT NULL
);
--
-- Name: holding_daily_snapshots_id_seq; Type: SEQUENCE; Schema: portfolio; Owner: -
--
ALTER TABLE portfolio.position_daily_snapshots
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME portfolio.holding_daily_snapshots_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: position_history; Type: TABLE; Schema: portfolio; Owner: -
--
CREATE TABLE portfolio.position_history (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    quantity numeric(38, 18) NOT NULL,
    position_id bigint NOT NULL,
    total numeric(38, 18) DEFAULT '-1'::integer NOT NULL,
    action_type character varying(255),
    CONSTRAINT holding_history_action_type_check CHECK (
        (
            (action_type)::text = ANY (
                (
                    ARRAY ['BUY'::character varying, 'SELL'::character varying, 'UNDO'::character varying]
                )::text []
            )
        )
    )
);
--
-- Name: holding_history_id_seq; Type: SEQUENCE; Schema: portfolio; Owner: -
--
ALTER TABLE portfolio.position_history
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME portfolio.holding_history_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: positions; Type: TABLE; Schema: portfolio; Owner: -
--
CREATE TABLE portfolio.positions (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    quantity numeric(38, 18) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    total numeric(20, 8) DEFAULT '-1.00'::numeric NOT NULL,
    portfolio_id bigint NOT NULL,
    instrument_id bigint,
    currency_code character varying(3) DEFAULT 'TRY'::character varying NOT NULL
);
--
-- Name: holdings_id_seq; Type: SEQUENCE; Schema: portfolio; Owner: -
--
ALTER TABLE portfolio.positions
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME portfolio.holdings_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: portfolios; Type: TABLE; Schema: portfolio; Owner: -
--
CREATE TABLE portfolio.portfolios (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    user_id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    archived boolean DEFAULT false,
    color character varying(7) DEFAULT '#3b82f6'::character varying NOT NULL
);
--
-- Name: portfolios_id_seq; Type: SEQUENCE; Schema: portfolio; Owner: -
--
ALTER TABLE portfolio.portfolios
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME portfolio.portfolios_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: transactions; Type: TABLE; Schema: portfolio; Owner: -
--
CREATE TABLE portfolio.transactions (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    price numeric(38, 18) NOT NULL,
    quantity numeric(38, 18) NOT NULL,
    commission numeric(38, 18) NOT NULL,
    type character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    position_id bigint NOT NULL,
    action_date timestamp(6) with time zone DEFAULT now() NOT NULL,
    new_quantity numeric(38, 18) DEFAULT 1 NOT NULL,
    new_total numeric(38, 18) DEFAULT 1 NOT NULL,
    notes text,
    metadata jsonb DEFAULT '{"tags": [], "notes": ""}'::jsonb NOT NULL,
    CONSTRAINT trades_type_check CHECK (
        (
            (type)::text = ANY (
                (
                    ARRAY ['BUY'::character varying, 'SELL'::character varying]
                )::text []
            )
        )
    )
);
--
-- Name: trades_id_seq; Type: SEQUENCE; Schema: portfolio; Owner: -
--
ALTER TABLE portfolio.transactions
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME portfolio.trades_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: transaction_performance; Type: TABLE; Schema: portfolio; Owner: -
--
CREATE TABLE portfolio.transaction_performance (
    created_at timestamp(6) with time zone NOT NULL,
    performance_category character varying(255) NOT NULL,
    profit numeric(38, 18) NOT NULL,
    return_percentage numeric(38, 18) NOT NULL,
    transaction_id bigint NOT NULL,
    CONSTRAINT trade_performance_performance_category_check CHECK (
        (
            (performance_category)::text = ANY (
                (
                    ARRAY ['EXCELLENT'::character varying, 'GOOD'::character varying, 'MODERATE'::character varying, 'POOR'::character varying]
                )::text []
            )
        )
    )
);
--
-- Name: batch_job_execution; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.batch_job_execution (
    job_execution_id bigint NOT NULL,
    version bigint,
    job_instance_id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    status character varying(10),
    exit_code character varying(2500),
    exit_message character varying(2500),
    last_updated timestamp without time zone
);
--
-- Name: batch_job_execution_context; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.batch_job_execution_context (
    job_execution_id bigint NOT NULL,
    short_context character varying(2500) NOT NULL,
    serialized_context text
);
--
-- Name: batch_job_execution_params; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.batch_job_execution_params (
    job_execution_id bigint NOT NULL,
    parameter_name character varying(100) NOT NULL,
    parameter_type character varying(100) NOT NULL,
    parameter_value character varying(2500),
    identifying character(1) NOT NULL
);
--
-- Name: batch_job_execution_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.batch_job_execution_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
--
-- Name: batch_job_instance; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.batch_job_instance (
    job_instance_id bigint NOT NULL,
    version bigint,
    job_name character varying(100) NOT NULL,
    job_key character varying(32) NOT NULL
);
--
-- Name: batch_job_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.batch_job_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
--
-- Name: batch_step_execution; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.batch_step_execution (
    step_execution_id bigint NOT NULL,
    version bigint NOT NULL,
    step_name character varying(100) NOT NULL,
    job_execution_id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    status character varying(10),
    commit_count bigint,
    read_count bigint,
    filter_count bigint,
    write_count bigint,
    read_skip_count bigint,
    write_skip_count bigint,
    process_skip_count bigint,
    rollback_count bigint,
    exit_code character varying(2500),
    exit_message character varying(2500),
    last_updated timestamp without time zone
);
--
-- Name: batch_step_execution_context; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.batch_step_execution_context (
    step_execution_id bigint NOT NULL,
    short_context character varying(2500) NOT NULL,
    serialized_context text
);
--
-- Name: batch_step_execution_seq; Type: SEQUENCE; Schema: public; Owner: -
--
CREATE SEQUENCE public.batch_step_execution_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
--
-- Name: countries; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.countries (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    iso_code character varying(3) NOT NULL,
    name character varying(255) NOT NULL
);
--
-- Name: countries_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
ALTER TABLE public.countries
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME public.countries_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: currencies; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.currencies (
    id bigint NOT NULL,
    code character varying(10) NOT NULL,
    name character varying(255) NOT NULL,
    symbol character varying(10) NOT NULL,
    decimals integer NOT NULL,
    exchange_rate numeric(38, 18) NOT NULL,
    exchange_rate_updated_at timestamp without time zone NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);
--
-- Name: currencies_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--
ALTER TABLE public.currencies
ALTER COLUMN id
ADD GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME public.currencies_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );
--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--
CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);
--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: account; Owner: -
--
ALTER TABLE ONLY account.permissions
ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);
--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: account; Owner: -
--
ALTER TABLE ONLY account.role_permissions
ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (id);
--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: account; Owner: -
--
ALTER TABLE ONLY account.roles
ADD CONSTRAINT roles_pkey PRIMARY KEY (id);
--
-- Name: users uk6dotkott2kjsp8vw4d0m25fb7; Type: CONSTRAINT; Schema: account; Owner: -
--
ALTER TABLE ONLY account.users
ADD CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);
--
-- Name: roles ukofx66keruapi6vyqpv6f2or37; Type: CONSTRAINT; Schema: account; Owner: -
--
ALTER TABLE ONLY account.roles
ADD CONSTRAINT ukofx66keruapi6vyqpv6f2or37 UNIQUE (name);
--
-- Name: permissions ukpnvtwliis6p05pn6i3ndjrqt2; Type: CONSTRAINT; Schema: account; Owner: -
--
ALTER TABLE ONLY account.permissions
ADD CONSTRAINT ukpnvtwliis6p05pn6i3ndjrqt2 UNIQUE (name);
--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: account; Owner: -
--
ALTER TABLE ONLY account.user_roles
ADD CONSTRAINT user_roles_pkey PRIMARY KEY (id);
--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: account; Owner: -
--
ALTER TABLE ONLY account.users
ADD CONSTRAINT users_pkey PRIMARY KEY (id);
--
-- Name: market_currencies market_currencies_pkey; Type: CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.market_currencies
ADD CONSTRAINT market_currencies_pkey PRIMARY KEY (market_id, currency_code);
--
-- Name: crypto_instruments pk_crypto_instruments; Type: CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.crypto_instruments
ADD CONSTRAINT pk_crypto_instruments PRIMARY KEY (instrument_id);
--
-- Name: instrument_snapshots pk_instrument_snapshots; Type: CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.instrument_snapshots
ADD CONSTRAINT pk_instrument_snapshots PRIMARY KEY (id);
--
-- Name: instruments pk_instruments; Type: CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.instruments
ADD CONSTRAINT pk_instruments PRIMARY KEY (id);
--
-- Name: markets pk_markets; Type: CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.markets
ADD CONSTRAINT pk_markets PRIMARY KEY (id);
--
-- Name: stock_instruments pk_stock_instruments; Type: CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.stock_instruments
ADD CONSTRAINT pk_stock_instruments PRIMARY KEY (instrument_id);
--
-- Name: instruments uc_instruments_symbol; Type: CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.instruments
ADD CONSTRAINT uc_instruments_symbol UNIQUE (symbol, market_id);
--
-- Name: markets uc_markets_name; Type: CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.markets
ADD CONSTRAINT uc_markets_name UNIQUE (name);
--
-- Name: instrument_snapshots uq_instrument_currency; Type: CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.instrument_snapshots
ADD CONSTRAINT uq_instrument_currency UNIQUE (instrument_id, currency_code);
--
-- Name: position_daily_snapshots holding_daily_snapshots_pkey; Type: CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.position_daily_snapshots
ADD CONSTRAINT holding_daily_snapshots_pkey PRIMARY KEY (id);
--
-- Name: position_history holding_history_pkey; Type: CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.position_history
ADD CONSTRAINT holding_history_pkey PRIMARY KEY (id);
--
-- Name: positions holdings_pkey; Type: CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.positions
ADD CONSTRAINT holdings_pkey PRIMARY KEY (id);
--
-- Name: dashboard_portfolios pk_dashboard_portfolios; Type: CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.dashboard_portfolios
ADD CONSTRAINT pk_dashboard_portfolios PRIMARY KEY (id);
--
-- Name: dashboards pk_dashboards; Type: CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.dashboards
ADD CONSTRAINT pk_dashboards PRIMARY KEY (id);
--
-- Name: portfolios pk_portfolios; Type: CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.portfolios
ADD CONSTRAINT pk_portfolios PRIMARY KEY (id);
--
-- Name: transaction_performance trade_performance_pkey; Type: CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.transaction_performance
ADD CONSTRAINT trade_performance_pkey PRIMARY KEY (transaction_id);
--
-- Name: transactions trades_pkey; Type: CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.transactions
ADD CONSTRAINT trades_pkey PRIMARY KEY (id);
--
-- Name: dashboard_portfolios ux_dashboard_portfolios_on_dashboard_id_and_portfolio_id; Type: CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.dashboard_portfolios
ADD CONSTRAINT ux_dashboard_portfolios_on_dashboard_id_and_portfolio_id UNIQUE (dashboard_id, portfolio_id);
--
-- Name: batch_job_execution_context batch_job_execution_context_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.batch_job_execution_context
ADD CONSTRAINT batch_job_execution_context_pkey PRIMARY KEY (job_execution_id);
--
-- Name: batch_job_execution batch_job_execution_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.batch_job_execution
ADD CONSTRAINT batch_job_execution_pkey PRIMARY KEY (job_execution_id);
--
-- Name: batch_job_instance batch_job_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.batch_job_instance
ADD CONSTRAINT batch_job_instance_pkey PRIMARY KEY (job_instance_id);
--
-- Name: batch_step_execution_context batch_step_execution_context_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.batch_step_execution_context
ADD CONSTRAINT batch_step_execution_context_pkey PRIMARY KEY (step_execution_id);
--
-- Name: batch_step_execution batch_step_execution_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.batch_step_execution
ADD CONSTRAINT batch_step_execution_pkey PRIMARY KEY (step_execution_id);
--
-- Name: countries countries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.countries
ADD CONSTRAINT countries_pkey PRIMARY KEY (id);
--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.flyway_schema_history
ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);
--
-- Name: batch_job_instance job_inst_un; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.batch_job_instance
ADD CONSTRAINT job_inst_un UNIQUE (job_name, job_key);
--
-- Name: currencies pk_currencies; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.currencies
ADD CONSTRAINT pk_currencies PRIMARY KEY (id);
--
-- Name: currencies uc_currencies_code; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.currencies
ADD CONSTRAINT uc_currencies_code UNIQUE (code);
--
-- Name: countries uk1pyiwrqimi3hnl3vtgsypj5r; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.countries
ADD CONSTRAINT uk1pyiwrqimi3hnl3vtgsypj5r UNIQUE (name);
--
-- Name: countries uk20ieiirrqjrlkw677k8fq6soj; Type: CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.countries
ADD CONSTRAINT uk20ieiirrqjrlkw677k8fq6soj UNIQUE (iso_code);
--
-- Name: idx_instruments_market; Type: INDEX; Schema: instrument; Owner: -
--
CREATE INDEX idx_instruments_market ON instrument.instruments USING btree (market_id);
--
-- Name: idx_instruments_symbol; Type: INDEX; Schema: instrument; Owner: -
--
CREATE INDEX idx_instruments_symbol ON instrument.instruments USING btree (symbol);
--
-- Name: idx_instruments_type; Type: INDEX; Schema: instrument; Owner: -
--
CREATE INDEX idx_instruments_type ON instrument.instruments USING btree (type);
--
-- Name: idx_holdings_portfolio_id; Type: INDEX; Schema: portfolio; Owner: -
--
CREATE INDEX idx_holdings_portfolio_id ON portfolio.positions USING btree (portfolio_id);
--
-- Name: idx_portfolio_user_id; Type: INDEX; Schema: portfolio; Owner: -
--
CREATE INDEX idx_portfolio_user_id ON portfolio.portfolios USING btree (user_id);
--
-- Name: idx_portfolios_user_id; Type: INDEX; Schema: portfolio; Owner: -
--
CREATE INDEX idx_portfolios_user_id ON portfolio.portfolios USING btree (user_id);
--
-- Name: idx_positions_portfolio_id; Type: INDEX; Schema: portfolio; Owner: -
--
CREATE INDEX idx_positions_portfolio_id ON portfolio.positions USING btree (portfolio_id);
--
-- Name: idx_transactions_action_date; Type: INDEX; Schema: portfolio; Owner: -
--
CREATE INDEX idx_transactions_action_date ON portfolio.transactions USING btree (action_date);
--
-- Name: idx_transactions_position_id; Type: INDEX; Schema: portfolio; Owner: -
--
CREATE INDEX idx_transactions_position_id ON portfolio.transactions USING btree (position_id);
--
-- Name: ux_dashboards_user_id_is_default_true; Type: INDEX; Schema: portfolio; Owner: -
--
CREATE UNIQUE INDEX ux_dashboards_user_id_is_default_true ON portfolio.dashboards USING btree (user_id)
WHERE (is_default = true);
--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--
CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);
--
-- Name: role_permissions fkegdk29eiy7mdtefy5c7eirr6e; Type: FK CONSTRAINT; Schema: account; Owner: -
--
ALTER TABLE ONLY account.role_permissions
ADD CONSTRAINT fkegdk29eiy7mdtefy5c7eirr6e FOREIGN KEY (permission_id) REFERENCES account.permissions(id);
--
-- Name: user_roles fkh8ciramu9cc9q3qcqiv4ue8a6; Type: FK CONSTRAINT; Schema: account; Owner: -
--
ALTER TABLE ONLY account.user_roles
ADD CONSTRAINT fkh8ciramu9cc9q3qcqiv4ue8a6 FOREIGN KEY (role_id) REFERENCES account.roles(id);
--
-- Name: user_roles fkhfh9dx7w3ubf1co1vdev94g3f; Type: FK CONSTRAINT; Schema: account; Owner: -
--
ALTER TABLE ONLY account.user_roles
ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES account.users(id);
--
-- Name: role_permissions fkn5fotdgk8d1xvo8nav9uv3muc; Type: FK CONSTRAINT; Schema: account; Owner: -
--
ALTER TABLE ONLY account.role_permissions
ADD CONSTRAINT fkn5fotdgk8d1xvo8nav9uv3muc FOREIGN KEY (role_id) REFERENCES account.roles(id);
--
-- Name: crypto_instruments fk_crypto_instruments_on_instrument; Type: FK CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.crypto_instruments
ADD CONSTRAINT fk_crypto_instruments_on_instrument FOREIGN KEY (instrument_id) REFERENCES instrument.instruments(id);
--
-- Name: instrument_snapshots fk_instrument_snapshots_on_instrument; Type: FK CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.instrument_snapshots
ADD CONSTRAINT fk_instrument_snapshots_on_instrument FOREIGN KEY (instrument_id) REFERENCES instrument.instruments(id);
--
-- Name: instruments fk_instruments_on_market; Type: FK CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.instruments
ADD CONSTRAINT fk_instruments_on_market FOREIGN KEY (market_id) REFERENCES instrument.markets(id);
--
-- Name: markets fk_markets_on_country; Type: FK CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.markets
ADD CONSTRAINT fk_markets_on_country FOREIGN KEY (country_id) REFERENCES public.countries(id);
--
-- Name: stock_instruments fk_stock_instruments_on_instrument; Type: FK CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.stock_instruments
ADD CONSTRAINT fk_stock_instruments_on_instrument FOREIGN KEY (instrument_id) REFERENCES instrument.instruments(id);
--
-- Name: market_currencies market_currencies_market_id_fkey; Type: FK CONSTRAINT; Schema: instrument; Owner: -
--
ALTER TABLE ONLY instrument.market_currencies
ADD CONSTRAINT market_currencies_market_id_fkey FOREIGN KEY (market_id) REFERENCES instrument.markets(id);
--
-- Name: position_history fk49rq4lvgdn71eui7nt3xfg8yl; Type: FK CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.position_history
ADD CONSTRAINT fk49rq4lvgdn71eui7nt3xfg8yl FOREIGN KEY (position_id) REFERENCES portfolio.positions(id);
--
-- Name: position_daily_snapshots fk4mq514o9flf0iayvg64cm2h9r; Type: FK CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.position_daily_snapshots
ADD CONSTRAINT fk4mq514o9flf0iayvg64cm2h9r FOREIGN KEY (position_id) REFERENCES portfolio.positions(id);
--
-- Name: transaction_performance fk9iwei2dph6ak0hd78adxh1ai5; Type: FK CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.transaction_performance
ADD CONSTRAINT fk9iwei2dph6ak0hd78adxh1ai5 FOREIGN KEY (transaction_id) REFERENCES portfolio.transactions(id);
--
-- Name: dashboard_portfolios fk_dashboard_portfolios_on_dashboard; Type: FK CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.dashboard_portfolios
ADD CONSTRAINT fk_dashboard_portfolios_on_dashboard FOREIGN KEY (dashboard_id) REFERENCES portfolio.dashboards(id);
--
-- Name: dashboard_portfolios fk_dashboard_portfolios_on_portfolio; Type: FK CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.dashboard_portfolios
ADD CONSTRAINT fk_dashboard_portfolios_on_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio.portfolios(id);
--
-- Name: dashboards fk_dashboards_on_currency; Type: FK CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.dashboards
ADD CONSTRAINT fk_dashboards_on_currency FOREIGN KEY (currency_id) REFERENCES public.currencies(id);
--
-- Name: dashboards fk_dashboards_on_user; Type: FK CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.dashboards
ADD CONSTRAINT fk_dashboards_on_user FOREIGN KEY (user_id) REFERENCES account.users(id);
--
-- Name: positions fk_holdings_on_portfolio; Type: FK CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.positions
ADD CONSTRAINT fk_holdings_on_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio.portfolios(id);
--
-- Name: portfolios fk_portfolios_on_user; Type: FK CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.portfolios
ADD CONSTRAINT fk_portfolios_on_user FOREIGN KEY (user_id) REFERENCES account.users(id);
--
-- Name: positions fk_positions_instrument; Type: FK CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.positions
ADD CONSTRAINT fk_positions_instrument FOREIGN KEY (instrument_id) REFERENCES instrument.instruments(id);
--
-- Name: transactions fkltmwmanjxv15jtw01wnyiobvn; Type: FK CONSTRAINT; Schema: portfolio; Owner: -
--
ALTER TABLE ONLY portfolio.transactions
ADD CONSTRAINT fkltmwmanjxv15jtw01wnyiobvn FOREIGN KEY (position_id) REFERENCES portfolio.positions(id);
--
-- Name: batch_job_execution_context job_exec_ctx_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.batch_job_execution_context
ADD CONSTRAINT job_exec_ctx_fk FOREIGN KEY (job_execution_id) REFERENCES public.batch_job_execution(job_execution_id);
--
-- Name: batch_job_execution_params job_exec_params_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.batch_job_execution_params
ADD CONSTRAINT job_exec_params_fk FOREIGN KEY (job_execution_id) REFERENCES public.batch_job_execution(job_execution_id);
--
-- Name: batch_step_execution job_exec_step_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.batch_step_execution
ADD CONSTRAINT job_exec_step_fk FOREIGN KEY (job_execution_id) REFERENCES public.batch_job_execution(job_execution_id);
--
-- Name: batch_job_execution job_inst_exec_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.batch_job_execution
ADD CONSTRAINT job_inst_exec_fk FOREIGN KEY (job_instance_id) REFERENCES public.batch_job_instance(job_instance_id);
--
-- Name: batch_step_execution_context step_exec_ctx_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--
ALTER TABLE ONLY public.batch_step_execution_context
ADD CONSTRAINT step_exec_ctx_fk FOREIGN KEY (step_execution_id) REFERENCES public.batch_step_execution(step_execution_id);
--
-- PostgreSQL database dump complete
--