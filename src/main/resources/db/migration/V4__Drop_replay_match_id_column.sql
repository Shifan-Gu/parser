-- Remove legacy replay_match_id artifacts now that match_id stores the canonical identifier

ALTER TABLE IF EXISTS replay_raw.game_info
    DROP COLUMN IF EXISTS replay_match_id;

DROP INDEX IF EXISTS replay_raw.idx_game_info_replay_match_id;


