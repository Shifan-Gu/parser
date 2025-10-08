-- Initialize the database with proper extensions and settings
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Set timezone
SET timezone = 'UTC';

-- Base fields that many tables share
-- match_id BIGINT NOT NULL
-- time INTEGER NOT NULL
-- created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP

-- Combat log events (DOTA_COMBATLOG_* types)
CREATE TABLE IF NOT EXISTS combat_log_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    type VARCHAR(50) NOT NULL,
    attackername VARCHAR(100),
    targetname VARCHAR(100),
    sourcename VARCHAR(100),
    targetsourcename VARCHAR(100),
    attackerhero BOOLEAN,
    targethero BOOLEAN,
    attackerillusion BOOLEAN,
    targetillusion BOOLEAN,
    inflictor VARCHAR(100),
    value INTEGER,
    valuename VARCHAR(100),
    gold_reason INTEGER,
    xp_reason INTEGER,
    stun_duration REAL,
    slow_duration REAL,
    greevils_greed_stack INTEGER,
    tracked_death BOOLEAN,
    tracked_sourcename VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Player actions
CREATE TABLE IF NOT EXISTS action_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    slot INTEGER,
    key VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Player pings
CREATE TABLE IF NOT EXISTS ping_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    slot INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Chat events (numbered types)
CREATE TABLE IF NOT EXISTS chat_type_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    type VARCHAR(50) NOT NULL,
    player1 INTEGER,
    player2 INTEGER,
    value INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Chat messages
CREATE TABLE IF NOT EXISTS chat_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    slot INTEGER,
    unit VARCHAR(100),
    key TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Chat wheel
CREATE TABLE IF NOT EXISTS chatwheel_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    slot INTEGER,
    key VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Cosmetics (global event)
CREATE TABLE IF NOT EXISTS cosmetics_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    key TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Dota Plus (global event)
CREATE TABLE IF NOT EXISTS dotaplus_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    key TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Epilogue (global event)
CREATE TABLE IF NOT EXISTS epilogue_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    key TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Neutral tokens
CREATE TABLE IF NOT EXISTS neutral_token_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    slot INTEGER,
    key VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Neutral item history
CREATE TABLE IF NOT EXISTS neutral_item_history_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    slot INTEGER,
    key VARCHAR(100),
    isNeutralActiveDrop BOOLEAN,
    isNeutralPassiveDrop BOOLEAN,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Player slots
CREATE TABLE IF NOT EXISTS player_slot_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    key VARCHAR(50),
    value INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Draft start
CREATE TABLE IF NOT EXISTS draft_start_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Draft timings
CREATE TABLE IF NOT EXISTS draft_timing_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    draft_order INTEGER,
    pick BOOLEAN,
    hero_id INTEGER,
    draft_active_team INTEGER,
    draft_extime0 INTEGER,
    draft_extime1 INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Interval events (periodic player state snapshots)
CREATE TABLE IF NOT EXISTS interval_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    slot INTEGER,
    unit VARCHAR(100),
    hero_id INTEGER,
    variant INTEGER,
    facet_hero_id INTEGER,
    level INTEGER,
    x REAL,
    y REAL,
    life_state INTEGER,
    gold INTEGER,
    lh INTEGER,
    xp INTEGER,
    stuns REAL,
    kills INTEGER,
    deaths INTEGER,
    assists INTEGER,
    denies INTEGER,
    obs_placed INTEGER,
    sen_placed INTEGER,
    creeps_stacked INTEGER,
    camps_stacked INTEGER,
    rune_pickups INTEGER,
    towers_killed INTEGER,
    roshans_killed INTEGER,
    observers_placed INTEGER,
    networth INTEGER,
    repicked BOOLEAN,
    randomed BOOLEAN,
    pred_vict BOOLEAN,
    firstblood_claimed INTEGER,
    teamfight_participation REAL,
    stage INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Ability level events
CREATE TABLE IF NOT EXISTS ability_level_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    targetname VARCHAR(100),
    valuename VARCHAR(100),
    abilitylevel INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Starting items
CREATE TABLE IF NOT EXISTS starting_item_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    slot INTEGER,
    targetname VARCHAR(100),
    valuename VARCHAR(100),
    value INTEGER,
    itemslot INTEGER,
    charges INTEGER,
    secondary_charges INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Game paused events
CREATE TABLE IF NOT EXISTS game_paused_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    key VARCHAR(50),
    value INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Ward events (observer and sentry wards)
CREATE TABLE IF NOT EXISTS ward_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    type VARCHAR(20) NOT NULL,
    slot INTEGER,
    x REAL,
    y REAL,
    z REAL,
    entityleft BOOLEAN,
    ehandle INTEGER,
    attackername VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_combat_log_match_id ON combat_log_events(match_id);
CREATE INDEX IF NOT EXISTS idx_combat_log_type ON combat_log_events(type);
CREATE INDEX IF NOT EXISTS idx_combat_log_time ON combat_log_events(time);

CREATE INDEX IF NOT EXISTS idx_action_match_id ON action_events(match_id);
CREATE INDEX IF NOT EXISTS idx_action_slot ON action_events(slot);

CREATE INDEX IF NOT EXISTS idx_ping_match_id ON ping_events(match_id);
CREATE INDEX IF NOT EXISTS idx_ping_slot ON ping_events(slot);

CREATE INDEX IF NOT EXISTS idx_chat_type_match_id ON chat_type_events(match_id);
CREATE INDEX IF NOT EXISTS idx_chat_match_id ON chat_events(match_id);
CREATE INDEX IF NOT EXISTS idx_chatwheel_match_id ON chatwheel_events(match_id);

CREATE INDEX IF NOT EXISTS idx_interval_match_id ON interval_events(match_id);
CREATE INDEX IF NOT EXISTS idx_interval_slot ON interval_events(slot);
CREATE INDEX IF NOT EXISTS idx_interval_hero_id ON interval_events(hero_id);
CREATE INDEX IF NOT EXISTS idx_interval_time ON interval_events(time);

CREATE INDEX IF NOT EXISTS idx_draft_timing_match_id ON draft_timing_events(match_id);
CREATE INDEX IF NOT EXISTS idx_draft_timing_order ON draft_timing_events(draft_order);

CREATE INDEX IF NOT EXISTS idx_ability_level_match_id ON ability_level_events(match_id);
CREATE INDEX IF NOT EXISTS idx_starting_item_match_id ON starting_item_events(match_id);

CREATE INDEX IF NOT EXISTS idx_ward_match_id ON ward_events(match_id);
CREATE INDEX IF NOT EXISTS idx_ward_type ON ward_events(type);
CREATE INDEX IF NOT EXISTS idx_ward_slot ON ward_events(slot);

-- Create views for common queries
CREATE OR REPLACE VIEW match_summary AS
SELECT 
    match_id,
    COUNT(*) as total_events,
    MIN(time) as game_start_time,
    MAX(time) as game_end_time,
    created_at
FROM interval_events 
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
FROM interval_events 
WHERE slot IS NOT NULL
GROUP BY match_id, slot, hero_id;
