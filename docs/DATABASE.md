# Database Integration for Dota 2 Parser

This document describes the PostgreSQL database integration for the Dota 2 replay parser.

## Overview

The parser now supports saving all parsed game events to a PostgreSQL database, providing structured storage and querying capabilities for replay analysis.

## Features

- **Automatic Database Creation**: Creates database and tables if they don't exist
- **Batch Processing**: Efficient batch inserts for better performance
- **Connection Pooling**: Uses HikariCP for optimal database connections
- **Comprehensive Schema**: Stores all game events with proper indexing
- **Docker Support**: Full Docker Compose setup with PostgreSQL
- **Query Tools**: Scripts for common database operations

## Database Schema

### Main Table: `game_events`

The `game_events` table stores all parsed game events with the following key columns:

- `match_id`: Unique identifier for each match
- `time`: Game time in seconds
- `type`: Event type (e.g., 'interval', 'DOTA_COMBATLOG_DEATH', etc.)
- `slot`: Player slot (0-9)
- `hero_id`: Hero identifier
- `x`, `y`, `z`: Position coordinates
- `gold`, `lh`, `xp`: Player resources
- `kills`, `deaths`, `assists`: Combat statistics
- And many more fields for comprehensive game data

### Views

- `match_summary`: Aggregated match statistics
- `player_stats`: Per-player performance metrics

## Setup

### Option 1: Docker Compose (Recommended)

```bash
# Start PostgreSQL and parser with database integration
docker-compose up -d

# The parser will automatically connect to the database
```

### Option 2: Manual Setup

1. **Install PostgreSQL** on your system

2. **Run the setup script**:
```bash
./scripts/setup_database.sh
```

3. **Set environment variables**:
```bash
export DB_ENABLED=true
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=dota_parser
export DB_USER=postgres
export DB_PASSWORD=postgres
```

4. **Run the parser**:
```bash
mvn clean install
java -jar target/stats-0.1.0.jar
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_ENABLED` | `false` | Enable database integration |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `dota_parser` | Database name |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `MATCH_ID` | auto-generated | Specific match ID to use |

## Usage Examples

### Query Recent Matches
```bash
./scripts/query_database.sh --matches
```

### View Player Statistics
```bash
./scripts/query_database.sh --player-stats
```

### Analyze Specific Match
```bash
./scripts/query_database.sh --match-id 12345
```

### Custom Queries
```bash
./scripts/query_database.sh --custom "SELECT type, COUNT(*) FROM game_events GROUP BY type ORDER BY COUNT(*) DESC;"
```

## Performance Considerations

- **Batch Size**: Default batch size is 1000 events for optimal performance
- **Indexes**: Proper indexing on commonly queried columns
- **Connection Pooling**: Configured for high-throughput scenarios
- **Memory Usage**: Batch processing prevents memory issues with large replays

## Data Types Stored

The parser captures and stores:

- **Player Statistics**: Gold, XP, kills, deaths, assists, last hits, denies
- **Position Data**: X, Y, Z coordinates for movement tracking
- **Combat Events**: Damage, healing, ability usage, item purchases
- **Game State**: Draft picks, pauses, team fights
- **Ward Activity**: Observer and sentry ward placements/destruction
- **Hero Abilities**: Ability levels and usage tracking

## Troubleshooting

### Database Connection Issues
- Ensure PostgreSQL is running and accessible
- Check firewall settings for port 5432
- Verify credentials and database exists

### Performance Issues
- Monitor database connection pool usage
- Consider adjusting batch size for your hardware
- Use appropriate indexes for your query patterns

### Memory Issues
- Reduce batch size if experiencing memory pressure
- Monitor JVM heap usage during parsing

## Development

### Adding New Fields
1. Update the `game_events` table schema
2. Modify `GameEventDAO.insertEvent()` method
3. Update the initialization script

### Custom Queries
Use the provided query script or connect directly to PostgreSQL:
```bash
psql -h localhost -p 5432 -U postgres -d dota_parser
```

## Security Notes

- Change default database passwords in production
- Use environment variables for sensitive configuration
- Consider SSL connections for remote databases
- Implement proper backup strategies for production data
