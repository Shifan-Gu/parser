CREATE TABLE IF NOT EXISTS game_info (
    match_id BIGINT PRIMARY KEY,
    replay_match_id BIGINT,
    playback_time REAL,
    playback_ticks INTEGER,
    playback_frames INTEGER,
    game_mode INTEGER,
    game_winner INTEGER,
    league_id INTEGER,
    radiant_team_id INTEGER,
    dire_team_id INTEGER,
    radiant_team_tag TEXT,
    dire_team_tag TEXT,
    end_time INTEGER,
    players JSONB,
    picks_bans JSONB,
    raw_file_info JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_game_info_replay_match_id ON game_info(replay_match_id);

