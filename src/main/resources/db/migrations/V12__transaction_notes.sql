DROP TABLE IF EXISTS public.tags;

ALTER TABLE portfolio.transactions
    DROP COLUMN IF EXISTS notes,
    ADD COLUMN metadata JSONB not null default
                                           '{
                                             "notes": "",
                                             "tags": []
                                           }'::jsonb;
