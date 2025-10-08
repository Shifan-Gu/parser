# Position Tracking - Quick Start Guide

## Enable Position Tracking (3 Steps)

### 1. Set Environment Variables
```bash
export DB_ENABLED=true
export POSITION_TRACKING_ENABLED=true
export POSITION_SAMPLE_RATE=0.1  # Optional: default is 0.1 (10Hz)
```

### 2. Start the Parser
```bash
# With Docker Compose
docker-compose -f docker-compose.dev.yml up

# OR manually
java -jar target/stats-0.1.0.jar
```

### 3. Parse a Replay
```bash
curl -X POST -T replay.dem http://localhost:5600
```

## Quick Queries

### Count Position Samples
```sql
SELECT COUNT(*) FROM position_events WHERE match_id = YOUR_MATCH_ID;
```

### View Hero Positions
```sql
SELECT time, slot, hero_id, x, y, life_state
FROM position_events
WHERE match_id = YOUR_MATCH_ID
  AND slot = 0  -- Player slot 0-9
ORDER BY time
LIMIT 100;
```

### Calculate Distance Traveled
```sql
WITH pos AS (
    SELECT slot, x, y,
           LAG(x) OVER (PARTITION BY slot ORDER BY time) as px,
           LAG(y) OVER (PARTITION BY slot ORDER BY time) as py
    FROM position_events WHERE match_id = YOUR_MATCH_ID
)
SELECT slot, 
       SUM(SQRT(POWER(x-px,2) + POWER(y-py,2))) as distance
FROM pos WHERE px IS NOT NULL GROUP BY slot;
```

### Generate Heatmap
```sql
SELECT 
    slot,
    FLOOR(x/10)*10 as grid_x,
    FLOOR(y/10)*10 as grid_y,
    COUNT(*) as visits
FROM position_events
WHERE match_id = YOUR_MATCH_ID
  AND life_state = 0
GROUP BY slot, grid_x, grid_y
HAVING COUNT(*) > 5
ORDER BY visits DESC;
```

## Configuration Options

| Variable | Default | Effect |
|----------|---------|--------|
| `POSITION_TRACKING_ENABLED` | `true` | On/off switch |
| `POSITION_SAMPLE_RATE` | `0.1` | Seconds between samples |
| `DB_ENABLED` | `false` | Must be `true` |

### Sample Rate Examples
```bash
# High frequency (10 samples/sec) - detailed but large
POSITION_SAMPLE_RATE=0.1

# Medium frequency (2 samples/sec) - balanced
POSITION_SAMPLE_RATE=0.5

# Low frequency (1 sample/sec) - same as intervals
POSITION_SAMPLE_RATE=1.0
```

## Test Your Setup

```bash
# Run the position tracking test
bash scripts/test/test_position_tracking.sh
```

## Disable Position Tracking

```bash
export POSITION_TRACKING_ENABLED=false
```

## Storage Impact

| Sample Rate | Samples/Match | Storage |
|-------------|---------------|---------|
| 0.1s (10Hz) | ~36,000 | ~3 MB |
| 0.5s (2Hz)  | ~7,200  | ~600 KB |
| 1.0s (1Hz)  | ~3,600  | ~300 KB |

*Based on 60-minute match, 10 heroes*

## Common Use Cases

1. **Movement Visualization**: Draw hero paths on map
2. **Heatmaps**: Show where heroes spend time
3. **Teamfight Analysis**: Detect when heroes group
4. **Gank Detection**: Multiple heroes converging
5. **Death Locations**: Position at time of death
6. **Speed Analysis**: Movement speed over time
7. **Lane Assignment**: Early game positioning
8. **Ward Optimization**: Cross-reference with paths

## Troubleshooting

### No data in database?
- Check `DB_ENABLED=true`
- Check `POSITION_TRACKING_ENABLED=true`
- Verify database is running
- Check parser logs for errors

### Too much data?
- Increase `POSITION_SAMPLE_RATE` (e.g., `0.5` or `1.0`)
- Consider data retention policies
- Filter queries by time range

### Queries slow?
- Use indexes (already created automatically)
- Filter by `match_id` first
- Add time range filters
- Consider materialized views

## Learn More

- **Full Guide**: `docs/POSITION_TRACKING.md`
- **Example Queries**: `database/migration_examples/V5__Example_position_queries.sql`
- **Implementation**: `POSITION_TRACKING_IMPLEMENTATION.md`
- **Database Setup**: `docs/DATABASE.md`
- **Flyway Migrations**: `docs/FLYWAY.md`

## Example Workflow

```bash
# 1. Configure
export DB_ENABLED=true
export POSITION_TRACKING_ENABLED=true
export POSITION_SAMPLE_RATE=0.1
export MATCH_ID=7503212404

# 2. Start services
docker-compose -f docker-compose.dev.yml up -d

# 3. Parse replay
curl -X POST -T test-data/7503212404.dem http://localhost:5600

# 4. Query data
psql -h localhost -U postgres -d parser -c \
  "SELECT COUNT(*) FROM position_events WHERE match_id = 7503212404;"

# 5. View movement summary
psql -h localhost -U postgres -d parser -c \
  "SELECT * FROM position_movement_summary WHERE match_id = 7503212404;"
```

## Quick Reference Card

```
┌─────────────────────────────────────────────────┐
│         POSITION TRACKING QUICK REF             │
├─────────────────────────────────────────────────┤
│ Enable:                                         │
│   export DB_ENABLED=true                        │
│   export POSITION_TRACKING_ENABLED=true         │
│                                                 │
│ Tables:                                         │
│   position_events     - Raw position data       │
│   interval_events     - Full state (1Hz)        │
│                                                 │
│ Columns:                                        │
│   match_id, time, slot, hero_id                 │
│   x, y, life_state, unit                        │
│                                                 │
│ Key Indexes:                                    │
│   (match_id, time)                              │
│   (match_id, slot)                              │
│                                                 │
│ Sample Rate:                                    │
│   0.1 = 10Hz (default, detailed)                │
│   0.5 = 2Hz (balanced)                          │
│   1.0 = 1Hz (minimal)                           │
│                                                 │
│ Test:                                           │
│   bash scripts/test/test_position_tracking.sh   │
└─────────────────────────────────────────────────┘
```

