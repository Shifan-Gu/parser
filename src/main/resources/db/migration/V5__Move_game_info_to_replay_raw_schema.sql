-- Ensure the game_info table lives in the replay_raw schema

CREATE SCHEMA IF NOT EXISTS replay_raw;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'game_info'
    ) THEN
        EXECUTE 'ALTER TABLE public.game_info SET SCHEMA replay_raw';
    END IF;
END $$;

