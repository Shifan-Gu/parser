# Dota Analytics DBT Project

This DBT project analyzes Dota 2 match data to calculate hero win rates and provide insights into hero performance.

## Overview

The project transforms raw game event data from a PostgreSQL database into analytical models that answer questions like:
- What is the win rate for each hero?
- How many matches has each hero been picked in?
- Which heroes have the highest/lowest win rates?

## Project Structure

```
dbt/
├── dbt_project.yml          # DBT project configuration
├── profiles.yml             # Database connection profiles
├── packages.yml             # DBT package dependencies
├── models/
│   ├── staging/            # Raw data cleaning and preparation
│   │   ├── sources.yml     # Source table definitions
│   │   ├── stg_draft_timing_events.sql
│   │   ├── stg_interval_events.sql
│   │   └── stg_combat_log_events.sql
│   ├── intermediate/       # Business logic transformations
│   │   ├── schema.yml
│   │   ├── int_match_winners.sql
│   │   └── int_hero_picks.sql
│   └── marts/              # Final analytical models
│       ├── schema.yml
│       ├── fct_hero_match_results.sql
│       └── hero_win_rates.sql     # Main output table
└── README.md
```

## Data Models

### Staging Layer
- **stg_draft_timing_events**: Cleaned draft timing events (picks only, no bans)
- **stg_interval_events**: Player state snapshots during matches
- **stg_combat_log_events**: Combat log events including building destruction

### Intermediate Layer
- **int_match_winners**: Determines which team won each match
  - Primary method: Ancient destruction events from combat logs
  - Fallback method: Team networth comparison at game end
- **int_hero_picks**: Maps heroes to matches and teams

### Marts Layer
- **fct_hero_match_results**: Fact table with each hero-match combination and outcome
- **hero_win_rates**: ⭐ Main output showing win rate statistics for each hero

## Setup

### Prerequisites
- Python 3.8+
- DBT Core installed (`pip install dbt-postgres`)
- PostgreSQL database with Dota 2 match data

### Environment Variables

Configure the following environment variables for database connection:
```bash
export DB_HOST=localhost
export DB_PORT=5433
export DB_USER=postgres
export DB_PASSWORD=your_password
export DB_NAME=dota
```

Alternatively, you can modify `profiles.yml` directly with your connection details.

### Installation

1. Install DBT dependencies:
```bash
cd dbt
dbt deps
```

2. Test database connection:
```bash
dbt debug
```

3. Run the models:
```bash
dbt run
```

4. Run tests:
```bash
dbt test
```

5. Generate and serve documentation:
```bash
dbt docs generate
dbt docs serve
```

## Usage

### Running Models

Run all models:
```bash
dbt run
```

Run specific model:
```bash
dbt run --select hero_win_rates
```

Run models and downstream dependencies:
```bash
dbt run --select int_match_winners+
```

### Querying Results

After running `dbt run`, query the main output table:

```sql
-- Get hero win rates
SELECT * FROM hero_win_rates
ORDER BY win_rate_pct DESC;

-- Get heroes with at least 10 matches
SELECT * FROM hero_win_rates
WHERE total_matches >= 10
ORDER BY win_rate_pct DESC;

-- Get detailed match results for a specific hero
SELECT * FROM fct_hero_match_results
WHERE hero_id = 1  -- Replace with desired hero ID
ORDER BY match_id DESC;
```

## Model Details

### hero_win_rates

The main output table with the following columns:

| Column | Type | Description |
|--------|------|-------------|
| hero_id | INTEGER | Numeric identifier for the hero |
| total_matches | INTEGER | Total matches where hero was picked |
| wins | INTEGER | Number of wins |
| losses | INTEGER | Number of losses |
| win_rate_pct | DECIMAL | Win rate as percentage (0-100) |
| win_rate_decimal | DECIMAL | Win rate as decimal (0-1) |

### Match Winner Determination

The project uses two methods to determine match winners:

1. **Ancient Destruction (Primary)**: Looks for combat log events where the ancient building is destroyed
   - `npc_dota_badguys_fort` destruction → Radiant (team 0) wins
   - `npc_dota_goodguys_fort` destruction → Dire (team 1) wins

2. **Networth Heuristic (Fallback)**: Compares team networth at game end
   - Team with higher total networth is considered the winner
   - Used when ancient destruction events are not found

## Testing

Run all tests:
```bash
dbt test
```

The project includes tests for:
- Data uniqueness and not-null constraints
- Accepted values for categorical fields
- Referential integrity between models

## Development

### Adding New Models

1. Create SQL file in appropriate directory (`staging/`, `intermediate/`, or `marts/`)
2. Add model documentation to corresponding `schema.yml`
3. Run the model: `dbt run --select your_model_name`
4. Add tests if needed

### DBT Best Practices

This project follows DBT best practices:
- ✅ Modular, layered architecture (staging → intermediate → marts)
- ✅ Comprehensive documentation
- ✅ Data quality tests
- ✅ Clear naming conventions
- ✅ DRY principle with refs and sources

## Troubleshooting

### Connection Issues
If you get connection errors:
1. Verify environment variables are set correctly
2. Check database is running: `docker ps` or `pg_isready`
3. Test connection: `dbt debug`

### No Data in Results
If hero_win_rates is empty:
1. Check source tables have data: `SELECT count(*) FROM draft_timing_events;`
2. Verify matches have both picks and results
3. Check int_match_winners has records

### Query Performance
For large datasets:
- Add indexes on `match_id`, `hero_id` columns
- Consider incremental materialization for large fact tables
- Use `dbt run --full-refresh` sparingly

## Future Enhancements

Potential additions to the project:
- Hero win rates by patch/version
- Win rates by team composition
- Hero synergy analysis
- Time-based trend analysis
- Player-specific hero statistics
- Hero win rates by game duration

## License

This project follows the same license as the parent repository.

## Support

For issues or questions:
1. Check DBT documentation: https://docs.getdbt.com/
2. Review model SQL and documentation
3. Check database connection and data availability

