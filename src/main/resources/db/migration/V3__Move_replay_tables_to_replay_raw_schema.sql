-- Move parsed replay data tables into dedicated replay_raw schema

CREATE SCHEMA IF NOT EXISTS replay_raw;

-- Drop dependent views prior to moving base tables
DROP VIEW IF EXISTS match_summary;
DROP VIEW IF EXISTS player_stats;

-- Move tables into replay_raw schema
ALTER TABLE IF EXISTS public.combat_log_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.action_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.ping_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.chat_type_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.chat_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.chatwheel_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.cosmetics_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.dotaplus_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.epilogue_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.neutral_token_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.neutral_item_history_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.player_slot_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.draft_start_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.draft_timing_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.interval_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.ability_level_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.starting_item_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.game_paused_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.ward_events SET SCHEMA replay_raw;
ALTER TABLE IF EXISTS public.game_info SET SCHEMA replay_raw;

-- Move associated sequences to replay_raw schema
ALTER SEQUENCE IF EXISTS public.combat_log_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.action_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.ping_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.chat_type_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.chat_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.chatwheel_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.cosmetics_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.dotaplus_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.epilogue_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.neutral_token_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.neutral_item_history_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.player_slot_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.draft_start_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.draft_timing_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.interval_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.ability_level_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.starting_item_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.game_paused_events_id_seq SET SCHEMA replay_raw;
ALTER SEQUENCE IF EXISTS public.ward_events_id_seq SET SCHEMA replay_raw;

-- Recreate views referencing tables in their new schema
CREATE OR REPLACE VIEW match_summary AS
SELECT 
    match_id,
    COUNT(*) AS total_events,
    MIN(time) AS game_start_time,
    MAX(time) AS game_end_time,
    created_at
FROM replay_raw.interval_events
GROUP BY match_id, created_at;

CREATE OR REPLACE VIEW player_stats AS
SELECT 
    match_id,
    slot,
    hero_id,
    MAX(level) AS max_level,
    MAX(gold) AS max_gold,
    MAX(lh) AS last_hits,
    MAX(denies) AS denies,
    MAX(kills) AS kills,
    MAX(deaths) AS deaths,
    MAX(assists) AS assists,
    MAX(networth) AS networth
FROM replay_raw.interval_events
WHERE slot IS NOT NULL
GROUP BY match_id, slot, hero_id;


