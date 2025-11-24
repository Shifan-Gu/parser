-- Create normalized tables for players and picks_bans from game_info
-- This replaces storing these as JSONB columns
-- Tables are created in the replay_raw schema

-- Ensure replay_raw schema exists
CREATE SCHEMA IF NOT EXISTS replay_raw;

-- Game players table - normalized from game_info.players JSONB
CREATE TABLE IF NOT EXISTS replay_raw.game_players (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    player_slot INTEGER,
    steam_id BIGINT,
    player_name TEXT,
    hero_name TEXT,
    game_team INTEGER,
    is_fake_client BOOLEAN,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_game_players_match_id FOREIGN KEY (match_id) REFERENCES replay_raw.game_info(match_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_game_players_match_id ON replay_raw.game_players(match_id);
CREATE INDEX IF NOT EXISTS idx_game_players_player_slot ON replay_raw.game_players(player_slot);

-- Game picks_bans table - normalized from game_info.picks_bans JSONB
CREATE TABLE IF NOT EXISTS replay_raw.game_picks_bans (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    is_pick BOOLEAN,
    team INTEGER,
    hero_id INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_game_picks_bans_match_id FOREIGN KEY (match_id) REFERENCES replay_raw.game_info(match_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_game_picks_bans_match_id ON replay_raw.game_picks_bans(match_id);
CREATE INDEX IF NOT EXISTS idx_game_picks_bans_hero_id ON replay_raw.game_picks_bans(hero_id);

