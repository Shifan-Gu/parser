# Position Tracking Implementation Summary

This document summarizes the position tracking feature implementation based on the [clarity-examples position pattern](https://github.com/skadistats/clarity-examples/blob/master/src/main/java/skadistats/clarity/examples/position/Main.java).

## Implementation Date
October 8, 2025

## Overview

High-frequency position tracking has been implemented to parse and store hero position data from Dota 2 replays at configurable rates (default: 10 samples per second, 10x more frequent than interval events).

## Files Created

### Database Migration
- **`src/main/resources/db/migration/V2__Add_position_tracking.sql`**
  - Creates `position_events` table for storing position data
  - Adds indexes for efficient querying
  - Creates `position_movement_summary` view for analytics

### Documentation
- **`docs/POSITION_TRACKING.md`**
  - Complete guide for position tracking feature
  - Configuration instructions
  - Query examples and use cases
  - Performance considerations

- **`database/migration_examples/V5__Example_position_queries.sql`**
  - 8 example queries demonstrating position data usage
  - Includes: movement paths, distance calculations, proximity detection, heatmaps, etc.

### Test Scripts
- **`scripts/test/test_position_tracking.sh`**
  - Test script for validating position tracking
  - Includes sample queries and verification

## Files Modified

### Java Source Code

#### `src/main/java/opendota/Parse.java`
**Changes:**
- Added position tracking configuration variables
  - `POSITION_SAMPLE_RATE` (default: 0.1s)
  - `nextPositionSample` for tracking sample timing
  - `positionTrackingEnabled` flag

- Added environment variable configuration in constructor
  - `POSITION_TRACKING_ENABLED`
  - `POSITION_SAMPLE_RATE`

- Implemented `trackHeroPositions()` method
  - Extracts position data for all 10 heroes
  - Uses `CBodyComponent.m_cellX/Y` and `CBodyComponent.m_vecX/Y`
  - Calculates precise positions via `getPreciseLocation()`
  - Creates position entries with type "position"

- Added position tracking call in `onTickStart()`
  - Runs at configured sample rate
  - Only during active game (not pre/post game)

#### `src/main/java/opendota/database/GameEventDAO.java`
**Changes:**
- Added `positionStmt` PreparedStatement
- Created `insertPositionEvent()` method
  - Validates position data before insertion
  - Uses batch processing for performance
  - Includes: match_id, time, slot, unit, hero_id, x, y, life_state

- Added position statement handling in:
  - `initializePreparedStatements()` - initialization
  - `insertEvent()` - routing
  - `executeBatch()` - batch execution
  - `close()` - cleanup

### Documentation Updates

#### `README.md`
**Changes:**
- Added position tracking to Features list
- Added Position Tracking section with configuration
- Updated Project Structure to include POSITION_TRACKING.md
- Added POSITION_TRACKING.md to Documentation section

## Key Features

### 1. Configurable Sample Rate
```bash
# 10 samples per second (default)
POSITION_SAMPLE_RATE=0.1

# 2 samples per second (lower storage)
POSITION_SAMPLE_RATE=0.5

# 1 sample per second (same as interval)
POSITION_SAMPLE_RATE=1.0
```

### 2. Enable/Disable Control
```bash
# Enabled by default
POSITION_TRACKING_ENABLED=true

# Disable for specific matches
POSITION_TRACKING_ENABLED=false
```

### 3. Database Integration
- Automatic batch processing (1000 events per batch)
- Indexed for efficient queries
- Integrated with existing database infrastructure
- Supports Flyway migrations

### 4. Data Validation
- Only stores positions when valid coordinates exist
- Tracks life_state (alive/dying/dead)
- Includes hero_id and slot for player identification
- Includes unit name for entity type

## Technical Details

### Position Calculation
Uses the Clarity library's coordinate system:
```java
Float getPreciseLocation(Integer cell, Float vec) {
    return (cell * 128.0f + vec) / 128;
}
```

This combines:
- **Cell coordinates**: Coarse grid position
- **Vector coordinates**: Fine position within cell
- Result is precise map coordinates

### Database Schema
```sql
CREATE TABLE position_events (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    time INTEGER NOT NULL,        -- Game time in seconds
    slot INTEGER NOT NULL,         -- Player slot (0-9)
    unit VARCHAR(100),             -- Hero entity name
    hero_id INTEGER,               -- Hero ID
    x REAL NOT NULL,               -- X coordinate
    y REAL NOT NULL,               -- Y coordinate
    life_state INTEGER,            -- 0=alive, 1=dying, 2=dead
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Performance Characteristics

**Default Configuration (0.1s sample rate):**
- ~10 samples/second/hero
- ~100 samples/second total (10 heroes)
- ~36,000 samples per match (60min match)
- ~2-3 MB storage per match

**Optimizations:**
- Batch inserts (1000 records)
- Prepared statements
- Multiple indexes for common queries
- Efficient storage (REAL for coordinates)

## Use Cases

### 1. Movement Visualization
Query consecutive positions to draw hero paths on map

### 2. Heatmap Generation
Grid-based aggregation shows where heroes spend time

### 3. Teamfight Analysis
Proximity detection identifies when heroes group up

### 4. Gank Detection
Speed analysis and multiple hero convergence

### 5. Ward Placement Optimization
Cross-reference with common paths

### 6. Death Location Analysis
Position when life_state changes

### 7. Distance Traveled Statistics
Sum of distances between consecutive points

### 8. Lane Assignment Detection
Position clustering in early game

## Example Queries

### Basic Position Query
```sql
SELECT time, x, y, life_state
FROM position_events
WHERE match_id = 7503212404
  AND slot = 0
ORDER BY time;
```

### Distance Traveled
```sql
WITH position_pairs AS (
    SELECT 
        slot,
        x, y,
        LAG(x) OVER (PARTITION BY slot ORDER BY time) as prev_x,
        LAG(y) OVER (PARTITION BY slot ORDER BY time) as prev_y
    FROM position_events
    WHERE match_id = 7503212404
)
SELECT 
    slot,
    SUM(SQRT(POWER(x - prev_x, 2) + POWER(y - prev_y, 2))) as distance
FROM position_pairs
WHERE prev_x IS NOT NULL
GROUP BY slot;
```

### Heatmap Data
```sql
SELECT 
    slot,
    FLOOR(x / 10) * 10 as grid_x,
    FLOOR(y / 10) * 10 as grid_y,
    COUNT(*) as visits
FROM position_events
WHERE match_id = 7503212404
  AND life_state = 0
GROUP BY slot, grid_x, grid_y
ORDER BY visits DESC;
```

## Testing

### Manual Testing
```bash
# Start services
docker-compose -f docker-compose.dev.yml up

# Run position tracking test
bash scripts/test/test_position_tracking.sh
```

### Database Verification
```bash
# Connect to database
psql -h localhost -U postgres -d parser

# Check for position data
SELECT COUNT(*) FROM position_events;

# View summary
SELECT * FROM position_movement_summary LIMIT 10;
```

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_ENABLED` | `false` | Must be `true` for position tracking |
| `POSITION_TRACKING_ENABLED` | `true` | Enable/disable position tracking |
| `POSITION_SAMPLE_RATE` | `0.1` | Sample rate in seconds |
| `MATCH_ID` | timestamp | Match ID for database records |

## Migration Path

1. **V1**: Initial schema with interval_events (1Hz position data)
2. **V2**: Added position_events table (configurable rate, default 10Hz)

The position tracking is additive and doesn't affect existing functionality.

## Comparison with Interval Events

| Aspect | interval_events | position_events |
|--------|----------------|-----------------|
| Frequency | 1 Hz | 10 Hz (configurable) |
| Data | Full player state | Position only |
| Size | ~20 MB/match | ~3 MB/match |
| Use case | Strategic analysis | Movement tracking |
| Storage | All stats | Coordinates + state |

## Future Enhancements

Potential improvements:
1. Configurable per-hero sampling rates
2. Dynamic sample rate based on movement speed
3. Position compression for static heroes
4. Real-time streaming support
5. Machine learning integration for prediction

## References

- [Clarity Examples - Position Tracking](https://github.com/skadistats/clarity-examples/blob/master/src/main/java/skadistats/clarity/examples/position/Main.java)
- [Clarity Library Documentation](https://github.com/skadistats/clarity)
- [Dota 2 Coordinate System](https://dota2.fandom.com/wiki/Minimap#Coordinates)

## Credits

Implementation based on the clarity-examples position tracking pattern by skadistats.

