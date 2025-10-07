-- Initialize the database with proper extensions and settings
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Set timezone
SET timezone = 'UTC';

-- Create the main game_events table
CREATE TABLE IF NOT EXISTS game_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    type VARCHAR(50),
    team INTEGER,
    unit VARCHAR(100),
    key TEXT,
    value INTEGER,
    slot INTEGER,
    player_slot INTEGER,
    player1 INTEGER,
    player2 INTEGER,
    attackername VARCHAR(100),
    targetname VARCHAR(100),
    sourcename VARCHAR(100),
    targetsourcename VARCHAR(100),
    attackerhero BOOLEAN,
    targethero BOOLEAN,
    attackerillusion BOOLEAN,
    targetillusion BOOLEAN,
    abilitylevel INTEGER,
    inflictor VARCHAR(100),
    gold_reason INTEGER,
    xp_reason INTEGER,
    valuename VARCHAR(100),
    gold INTEGER,
    lh INTEGER,
    xp INTEGER,
    x REAL,
    y REAL,
    z REAL,
    stuns REAL,
    hero_id INTEGER,
    variant INTEGER,
    facet_hero_id INTEGER,
    itemslot INTEGER,
    charges INTEGER,
    secondary_charges INTEGER,
    life_state INTEGER,
    level INTEGER,
    kills INTEGER,
    deaths INTEGER,
    assists INTEGER,
    denies INTEGER,
    entityleft BOOLEAN,
    ehandle INTEGER,
    isNeutralActiveDrop BOOLEAN,
    isNeutralPassiveDrop BOOLEAN,
    obs_placed INTEGER,
    sen_placed INTEGER,
    creeps_stacked INTEGER,
    camps_stacked INTEGER,
    rune_pickups INTEGER,
    repicked BOOLEAN,
    randomed BOOLEAN,
    pred_vict BOOLEAN,
    stun_duration REAL,
    slow_duration REAL,
    tracked_death BOOLEAN,
    greevils_greed_stack INTEGER,
    tracked_sourcename VARCHAR(100),
    firstblood_claimed INTEGER,
    teamfight_participation REAL,
    towers_killed INTEGER,
    roshans_killed INTEGER,
    observers_placed INTEGER,
    draft_order INTEGER,
    pick BOOLEAN,
    draft_active_team INTEGER,
    draft_extime0 INTEGER,
    draft_extime1 INTEGER,
    networth INTEGER,
    stage INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_game_events_match_id ON game_events(match_id);
CREATE INDEX IF NOT EXISTS idx_game_events_type ON game_events(type);
CREATE INDEX IF NOT EXISTS idx_game_events_slot ON game_events(slot);
CREATE INDEX IF NOT EXISTS idx_game_events_time ON game_events(time);
CREATE INDEX IF NOT EXISTS idx_game_events_hero_id ON game_events(hero_id);
CREATE INDEX IF NOT EXISTS idx_game_events_created_at ON game_events(created_at);
CREATE INDEX IF NOT EXISTS idx_game_events_team ON game_events(team);
CREATE INDEX IF NOT EXISTS idx_game_events_player_slot ON game_events(player_slot);

-- Create a view for common queries
CREATE OR REPLACE VIEW match_summary AS
SELECT 
    match_id,
    COUNT(*) as total_events,
    MIN(time) as game_start_time,
    MAX(time) as game_end_time,
    COUNT(DISTINCT slot) as players_count,
    COUNT(DISTINCT hero_id) as unique_heroes,
    created_at
FROM game_events 
WHERE type IS NOT NULL
GROUP BY match_id, created_at;

-- Create a view for player statistics
CREATE OR REPLACE VIEW player_stats AS
SELECT 
    match_id,
    slot,
    hero_id,
    MAX(level) as max_level,
    MAX(gold) as max_gold,
    MAX(lh) as last_hits,
    MAX(denies) as denies,
    MAX(kills) as kills,
    MAX(deaths) as deaths,
    MAX(assists) as assists,
    MAX(networth) as networth
FROM game_events 
WHERE type = 'interval' AND slot IS NOT NULL
GROUP BY match_id, slot, hero_id;
