-- Add position tracking table for high-frequency position data
-- This table stores position data more frequently than interval_events
-- Following the clarity-examples pattern for position tracking

-- Position events table for tracking hero positions at a higher frequency
CREATE TABLE IF NOT EXISTS position_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,
    slot INTEGER NOT NULL,
    unit VARCHAR(100),
    hero_id INTEGER,
    x REAL NOT NULL,
    y REAL NOT NULL,
    life_state INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_position_match_id ON position_events(match_id);
CREATE INDEX IF NOT EXISTS idx_position_slot ON position_events(slot);
CREATE INDEX IF NOT EXISTS idx_position_hero_id ON position_events(hero_id);
CREATE INDEX IF NOT EXISTS idx_position_time ON position_events(time);
CREATE INDEX IF NOT EXISTS idx_position_match_time ON position_events(match_id, time);
CREATE INDEX IF NOT EXISTS idx_position_match_slot ON position_events(match_id, slot);

-- Create a view for position tracking analytics
CREATE OR REPLACE VIEW position_movement_summary AS
SELECT 
    match_id,
    slot,
    hero_id,
    COUNT(*) as position_samples,
    MIN(time) as first_sample_time,
    MAX(time) as last_sample_time,
    AVG(x) as avg_x,
    AVG(y) as avg_y
FROM position_events 
WHERE slot IS NOT NULL
GROUP BY match_id, slot, hero_id;

-- Add comment for documentation
COMMENT ON TABLE position_events IS 'High-frequency position tracking data for hero entities, sampled more frequently than interval_events';
COMMENT ON COLUMN position_events.time IS 'Game time in seconds';
COMMENT ON COLUMN position_events.slot IS 'Player slot (0-9)';
COMMENT ON COLUMN position_events.x IS 'X coordinate on the map';
COMMENT ON COLUMN position_events.y IS 'Y coordinate on the map';
COMMENT ON COLUMN position_events.life_state IS 'Life state of the entity (0=alive, 1=dying, 2=dead)';

