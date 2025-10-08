-- Example migration: Adding a new table
-- Copy this to src/main/resources/db/migration/ and modify as needed
-- Rename to V2__Your_description.sql (or next available version number)

-- Create a new table for tracking player performance metrics
CREATE TABLE IF NOT EXISTS player_performance_metrics (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    slot INTEGER NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value REAL,
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes for common queries
CREATE INDEX IF NOT EXISTS idx_perf_match_id 
    ON player_performance_metrics(match_id);
    
CREATE INDEX IF NOT EXISTS idx_perf_slot 
    ON player_performance_metrics(slot);
    
CREATE INDEX IF NOT EXISTS idx_perf_metric 
    ON player_performance_metrics(metric_name);

-- Add comments for documentation
COMMENT ON TABLE player_performance_metrics IS 
    'Stores calculated performance metrics for players in each match';
    
COMMENT ON COLUMN player_performance_metrics.metric_name IS 
    'Name of the performance metric (e.g., kda_ratio, gpm, xpm)';

