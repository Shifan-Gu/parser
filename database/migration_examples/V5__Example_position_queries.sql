-- Example queries for position tracking data
-- This file demonstrates how to query and analyze position data
-- DO NOT run this as a migration - it's for reference only

-- Example 1: Get all positions for a specific hero in a match
-- Useful for drawing movement paths
SELECT 
    time,
    slot,
    hero_id,
    x,
    y,
    life_state
FROM position_events
WHERE match_id = 7503212404
    AND slot = 0  -- Player slot (0-9)
ORDER BY time;

-- Example 2: Calculate distance traveled by a hero
-- This uses the Pythagorean theorem to calculate distance between consecutive points
WITH position_pairs AS (
    SELECT 
        match_id,
        slot,
        hero_id,
        time,
        x,
        y,
        LAG(x) OVER (PARTITION BY match_id, slot ORDER BY time) as prev_x,
        LAG(y) OVER (PARTITION BY match_id, slot ORDER BY time) as prev_y
    FROM position_events
    WHERE match_id = 7503212404
)
SELECT 
    slot,
    hero_id,
    SUM(SQRT(POWER(x - prev_x, 2) + POWER(y - prev_y, 2))) as total_distance
FROM position_pairs
WHERE prev_x IS NOT NULL
GROUP BY slot, hero_id
ORDER BY slot;

-- Example 3: Find when heroes were in proximity to each other
-- Useful for analyzing teamfights or ganks
WITH hero_positions AS (
    SELECT 
        p1.match_id,
        p1.time,
        p1.slot as slot1,
        p1.hero_id as hero1,
        p1.x as x1,
        p1.y as y1,
        p2.slot as slot2,
        p2.hero_id as hero2,
        p2.x as x2,
        p2.y as y2,
        SQRT(POWER(p1.x - p2.x, 2) + POWER(p1.y - p2.y, 2)) as distance
    FROM position_events p1
    JOIN position_events p2 
        ON p1.match_id = p2.match_id 
        AND p1.time = p2.time
        AND p1.slot < p2.slot
    WHERE p1.match_id = 7503212404
)
SELECT 
    time,
    slot1,
    hero1,
    slot2,
    hero2,
    distance
FROM hero_positions
WHERE distance < 10  -- Within 1000 game units
ORDER BY time, distance;

-- Example 4: Heatmap data - count how often heroes visit map regions
-- Divides the map into a grid and counts visits
SELECT 
    slot,
    hero_id,
    FLOOR(x / 10) * 10 as grid_x,
    FLOOR(y / 10) * 10 as grid_y,
    COUNT(*) as visits
FROM position_events
WHERE match_id = 7503212404
    AND life_state = 0  -- Only count when alive
GROUP BY slot, hero_id, grid_x, grid_y
HAVING COUNT(*) > 5  -- Filter out infrequent visits
ORDER BY slot, visits DESC;

-- Example 5: Average position by game time segment
-- Shows where heroes tend to be during different phases
SELECT 
    slot,
    hero_id,
    CASE 
        WHEN time < 600 THEN 'Early Game (0-10 min)'
        WHEN time < 1200 THEN 'Mid Game (10-20 min)'
        WHEN time < 1800 THEN 'Late Game (20-30 min)'
        ELSE 'Very Late Game (30+ min)'
    END as game_phase,
    AVG(x) as avg_x,
    AVG(y) as avg_y,
    COUNT(*) as samples
FROM position_events
WHERE match_id = 7503212404
    AND life_state = 0
GROUP BY slot, hero_id, game_phase
ORDER BY slot, game_phase;

-- Example 6: Death locations
-- Find where heroes died by looking at position when life_state changed
SELECT 
    pe.match_id,
    pe.slot,
    pe.hero_id,
    pe.time,
    pe.x,
    pe.y,
    cle.type as death_type,
    cle.attackername
FROM position_events pe
LEFT JOIN combat_log_events cle 
    ON pe.match_id = cle.match_id 
    AND pe.time = cle.time
    AND cle.type = 'DOTA_COMBATLOG_DEATH'
WHERE pe.match_id = 7503212404
    AND pe.life_state IN (1, 2)  -- Dying or dead
ORDER BY pe.time;

-- Example 7: Speed analysis
-- Calculate movement speed at each point in time
WITH speed_calc AS (
    SELECT 
        match_id,
        slot,
        hero_id,
        time,
        x,
        y,
        LAG(x) OVER (PARTITION BY match_id, slot ORDER BY time) as prev_x,
        LAG(y) OVER (PARTITION BY match_id, slot ORDER BY time) as prev_y,
        LAG(time) OVER (PARTITION BY match_id, slot ORDER BY time) as prev_time
    FROM position_events
    WHERE match_id = 7503212404
        AND life_state = 0
)
SELECT 
    slot,
    hero_id,
    time,
    CASE 
        WHEN (time - prev_time) > 0 THEN
            SQRT(POWER(x - prev_x, 2) + POWER(y - prev_y, 2)) / (time - prev_time)
        ELSE 0
    END as speed
FROM speed_calc
WHERE prev_x IS NOT NULL
ORDER BY slot, time;

-- Example 8: Compare position sampling with interval data
-- Shows how position_events provides more granular data
SELECT 
    'Position Events' as source,
    COUNT(*) as sample_count,
    COUNT(DISTINCT time) as unique_times,
    MIN(time) as first_sample,
    MAX(time) as last_sample
FROM position_events
WHERE match_id = 7503212404
UNION ALL
SELECT 
    'Interval Events' as source,
    COUNT(*) as sample_count,
    COUNT(DISTINCT time) as unique_times,
    MIN(time) as first_sample,
    MAX(time) as last_sample
FROM interval_events
WHERE match_id = 7503212404;

