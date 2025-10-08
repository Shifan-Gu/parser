# Position Tracking

This document describes the position tracking feature that parses and stores hero position data from Dota 2 replays.

## Overview

The position tracking system captures hero positions at a high frequency (configurable, default 10 times per second) and stores them in the database. This provides much more granular position data compared to the standard interval events which only capture positions once per second.

## Implementation

The implementation follows the [clarity-examples position tracking pattern](https://github.com/skadistats/clarity-examples/blob/master/src/main/java/skadistats/clarity/examples/position/Main.java) and includes:

1. **Database Schema** (`V2__Add_position_tracking.sql`)
   - `position_events` table for storing position data
   - Indexed for efficient queries by match_id, slot, hero_id, and time
   - Position movement summary view for analytics

2. **DAO Layer** (`GameEventDAO.java`)
   - PreparedStatement for batch inserting position events
   - Integration with existing event processing pipeline

3. **Parser Integration** (`Parse.java`)
   - `trackHeroPositions()` method extracts position data for all heroes
   - Configurable sampling rate via environment variable
   - Can be enabled/disabled via environment variable

## Database Schema

### position_events Table

```sql
CREATE TABLE position_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,           -- Game time in seconds
    slot INTEGER NOT NULL,            -- Player slot (0-9)
    unit VARCHAR(100),                -- Entity name (e.g., CDOTA_Unit_Hero_Zuus)
    hero_id INTEGER,                  -- Hero ID
    x REAL NOT NULL,                  -- X coordinate
    y REAL NOT NULL,                  -- Y coordinate
    life_state INTEGER,               -- 0=alive, 1=dying, 2=dead
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Indexes

- `idx_position_match_id`: Query all positions for a match
- `idx_position_slot`: Query positions for a specific player
- `idx_position_hero_id`: Query positions for a specific hero
- `idx_position_time`: Query positions at a specific time
- `idx_position_match_time`: Compound index for time-series queries
- `idx_position_match_slot`: Compound index for player tracking

## Configuration

### Environment Variables

#### POSITION_TRACKING_ENABLED
Controls whether position tracking is enabled.

- **Default**: `true`
- **Values**: `true`, `false`, `1`, `0`
- **Example**: `POSITION_TRACKING_ENABLED=true`

#### POSITION_SAMPLE_RATE
Controls how frequently positions are sampled (in seconds).

- **Default**: `0.1` (10 samples per second)
- **Values**: Any positive float
- **Example**: `POSITION_SAMPLE_RATE=0.5` (2 samples per second)
- **Note**: Lower values = more data but higher storage/processing cost

#### DB_ENABLED
Must be enabled for position tracking to work (required for all database features).

- **Default**: `false`
- **Values**: `true`, `false`, `1`, `0`
- **Example**: `DB_ENABLED=true`

## Usage Examples

### Basic Query - Get All Positions for a Player

```sql
SELECT time, x, y, life_state
FROM position_events
WHERE match_id = 7503212404
    AND slot = 0  -- Player 1
ORDER BY time;
```

### Draw Movement Path

To create a replay visualization, query consecutive positions:

```sql
SELECT 
    time,
    slot,
    hero_id,
    x,
    y,
    life_state
FROM position_events
WHERE match_id = 7503212404
    AND slot = 0
    AND time BETWEEN 600 AND 900  -- Minutes 10-15
ORDER BY time;
```

### Calculate Distance Traveled

```sql
WITH position_pairs AS (
    SELECT 
        slot,
        hero_id,
        time,
        x,
        y,
        LAG(x) OVER (PARTITION BY slot ORDER BY time) as prev_x,
        LAG(y) OVER (PARTITION BY slot ORDER BY time) as prev_y
    FROM position_events
    WHERE match_id = 7503212404
)
SELECT 
    slot,
    hero_id,
    SUM(SQRT(POWER(x - prev_x, 2) + POWER(y - prev_y, 2))) as total_distance
FROM position_pairs
WHERE prev_x IS NOT NULL
GROUP BY slot, hero_id;
```

### Find Teamfight Locations

Identify when multiple heroes were close together:

```sql
WITH hero_positions AS (
    SELECT 
        p1.time,
        p1.slot as slot1,
        p2.slot as slot2,
        p1.x, p1.y,
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
    COUNT(DISTINCT slot1) + COUNT(DISTINCT slot2) as hero_count,
    AVG(x) as center_x,
    AVG(y) as center_y
FROM hero_positions
WHERE distance < 10  -- Within 1000 game units
GROUP BY time
HAVING COUNT(*) >= 3  -- At least 3 pairs = 4+ heroes
ORDER BY time;
```

### Generate Heatmap Data

For visualization tools, create a heatmap of hero presence:

```sql
SELECT 
    slot,
    FLOOR(x / 10) * 10 as grid_x,
    FLOOR(y / 10) * 10 as grid_y,
    COUNT(*) as visits
FROM position_events
WHERE match_id = 7503212404
    AND life_state = 0  -- Only alive
GROUP BY slot, grid_x, grid_y
ORDER BY visits DESC;
```

## Performance Considerations

### Storage Requirements

With default settings (0.1s sample rate):
- ~3,600 samples per hero per hour of gameplay
- ~36,000 total samples per match (10 heroes)
- ~2-3 MB per match (uncompressed)

### Query Optimization Tips

1. **Always filter by match_id first** - uses the primary index
2. **Use time ranges** for large datasets
3. **Filter by slot** when analyzing individual players
4. **Consider materialized views** for complex recurring queries
5. **Use EXPLAIN ANALYZE** to optimize custom queries

### Reducing Storage

If storage is a concern, you can:

1. **Increase sample rate**: Set `POSITION_SAMPLE_RATE=0.5` (5x less data)
2. **Filter dead heroes**: Only query `WHERE life_state = 0`
3. **Archive old matches**: Move to cold storage after analysis
4. **Use compression**: Enable PostgreSQL table compression

## Integration with Other Events

Position data can be joined with other event tables:

```sql
-- Find positions when kills happened
SELECT 
    p.time,
    p.slot,
    p.x,
    p.y,
    c.attackername,
    c.targetname
FROM position_events p
JOIN combat_log_events c 
    ON p.match_id = c.match_id 
    AND p.time = c.time
    AND c.type = 'DOTA_COMBATLOG_DEATH'
WHERE p.match_id = 7503212404;

-- Compare positions with interval stats
SELECT 
    p.time,
    p.slot,
    p.x,
    p.y,
    i.gold,
    i.xp,
    i.kills
FROM position_events p
LEFT JOIN interval_events i
    ON p.match_id = i.match_id
    AND p.slot = i.slot
    AND p.time = i.time
WHERE p.match_id = 7503212404;
```

## Differences from Interval Events

| Feature | interval_events | position_events |
|---------|----------------|-----------------|
| **Frequency** | 1 sample/second | Configurable (default 10/second) |
| **Data included** | Full player state (gold, XP, items, etc.) | Position only (lightweight) |
| **Primary use** | Game state analysis | Movement tracking, visualization |
| **Storage per match** | ~20 MB | ~3 MB (default rate) |
| **Best for** | Strategic analysis | Tactical movement analysis |

## Troubleshooting

### No position data in database

1. Check `DB_ENABLED=true` is set
2. Verify `POSITION_TRACKING_ENABLED` is not set to `false`
3. Check parser logs for errors
4. Verify database migrations ran successfully

### Too much data being generated

1. Increase `POSITION_SAMPLE_RATE` (e.g., `0.5` or `1.0`)
2. Consider disabling for old matches
3. Implement data retention policies

### Positions seem incorrect

1. Verify you're using `getPreciseLocation()` calculation
2. Check coordinate system (Dota 2 uses centered coordinates)
3. Compare with interval_events positions for validation

## Related Files

- `src/main/resources/db/migration/V2__Add_position_tracking.sql` - Database schema
- `src/main/java/opendota/database/GameEventDAO.java` - Data access layer
- `src/main/java/opendota/Parse.java` - Position tracking implementation
- `database/migration_examples/V5__Example_position_queries.sql` - Query examples

## References

- [Clarity Position Example](https://github.com/skadistats/clarity-examples/blob/master/src/main/java/skadistats/clarity/examples/position/Main.java)
- [Dota 2 Coordinate System](https://dota2.fandom.com/wiki/Minimap#Coordinates)
- Project Documentation: `docs/DATABASE.md`, `docs/FLYWAY.md`

