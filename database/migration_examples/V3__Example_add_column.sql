-- Example migration: Adding a column to an existing table
-- Copy this to src/main/resources/db/migration/ and modify as needed
-- Rename to V3__Your_description.sql (or next available version number)

-- Add a new column to track player MMR
ALTER TABLE interval_events 
ADD COLUMN IF NOT EXISTS player_mmr INTEGER;

-- Add an index if the column will be queried frequently
CREATE INDEX IF NOT EXISTS idx_interval_mmr 
    ON interval_events(player_mmr);

-- Add a comment explaining the new column
COMMENT ON COLUMN interval_events.player_mmr IS 
    'Player MMR (Match Making Rating) at the time of the match';

-- Example: Populate the column with default values if needed
-- UPDATE interval_events SET player_mmr = 0 WHERE player_mmr IS NULL;

