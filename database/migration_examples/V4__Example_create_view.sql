-- Example migration: Creating or updating a view
-- Copy this to src/main/resources/db/migration/ and modify as needed
-- Rename to V4__Your_description.sql (or next available version number)

-- Create or replace a view for hero statistics
CREATE OR REPLACE VIEW hero_statistics AS
SELECT 
    hero_id,
    COUNT(DISTINCT match_id) as matches_played,
    AVG(CAST(kills AS REAL)) as avg_kills,
    AVG(CAST(deaths AS REAL)) as avg_deaths,
    AVG(CAST(assists AS REAL)) as avg_assists,
    AVG(CAST(gold AS REAL)) as avg_gold,
    AVG(CAST(xp AS REAL)) as avg_xp,
    AVG(CAST(networth AS REAL)) as avg_networth
FROM interval_events
WHERE hero_id IS NOT NULL 
    AND time = (
        SELECT MAX(time) 
        FROM interval_events e2 
        WHERE e2.match_id = interval_events.match_id 
            AND e2.slot = interval_events.slot
    )
GROUP BY hero_id;

-- Add a comment to the view
COMMENT ON VIEW hero_statistics IS 
    'Aggregated statistics for each hero across all matches';

