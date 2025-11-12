# Quick Start Guide

Get up and running with the Dota Analytics DBT project in 5 minutes!

## Prerequisites

- Python 3.8 or higher
- PostgreSQL database with Dota 2 match data
- pip package manager

## Step 1: Install DBT

```bash
pip install dbt-postgres
```

## Step 2: Configure Database Connection

Set environment variables for your database:

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_USER=postgres
export DB_PASSWORD=your_password
export DB_NAME=dota
```

## Step 3: Navigate to DBT Directory

```bash
cd /Users/shifangu/Desktop/tidebound/parser/dbt
```

## Step 4: Install Dependencies

```bash
dbt deps
```

## Step 5: Test Connection

```bash
dbt debug
```

You should see "All checks passed!" if the connection is successful.

## Step 6: Run the Models

```bash
dbt run
```

This will create all the tables and views in your database.

## Step 7: View Results

Connect to your database and query the hero win rates:

```sql
SELECT 
    hero_id,
    total_matches,
    wins,
    losses,
    win_rate_pct
FROM hero_win_rates
ORDER BY total_matches DESC
LIMIT 10;
```

## Optional: Generate Documentation

```bash
dbt docs generate
dbt docs serve
```

This will open a browser with interactive documentation of your data models.

## Troubleshooting

### Issue: "Connection refused"
- Check that PostgreSQL is running
- Verify environment variables are set correctly
- Try: `pg_isready -h localhost -p 5432`

### Issue: "Relation does not exist"
- Ensure source tables exist in the database
- Check schema: `\dt` in psql
- Verify you're connected to the correct database

### Issue: "No data in hero_win_rates"
- Check source data exists: `SELECT count(*) FROM draft_timing_events;`
- Verify picks are present: `SELECT count(*) FROM draft_timing_events WHERE pick = true;`
- Check match winners: `SELECT count(*) FROM int_match_winners;`

## Next Steps

1. Explore the full [README.md](README.md) for detailed documentation
2. Run tests: `dbt test`
3. Customize models for your specific analysis needs
4. Add incremental models for better performance with large datasets

## Useful Commands

```bash
# Run all models
dbt run

# Run specific model
dbt run --select hero_win_rates

# Run models with dependencies
dbt run --select int_match_winners+

# Run tests
dbt test

# Run specific test
dbt test --select hero_win_rates

# Full refresh (rebuild all tables)
dbt run --full-refresh

# Compile SQL without running
dbt compile

# List all models
dbt ls
```

Happy analyzing! 🎮📊

