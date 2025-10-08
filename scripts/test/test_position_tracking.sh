#!/bin/bash
# Test script for position tracking functionality
# This script demonstrates how to test position tracking with the parser

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "=================================="
echo "Position Tracking Test"
echo "=================================="
echo ""

# Check if test data exists
TEST_REPLAY="$PROJECT_ROOT/test-data/7503212404.dem"
if [ ! -f "$TEST_REPLAY" ]; then
    echo "Error: Test replay file not found at $TEST_REPLAY"
    exit 1
fi

echo "Test replay found: $TEST_REPLAY"
echo ""

# Check if parser is running
if ! curl -s http://localhost:5600/healthz > /dev/null 2>&1; then
    echo "Error: Parser is not running on port 5600"
    echo "Start the parser with: docker-compose -f docker-compose.dev.yml up"
    exit 1
fi

echo "Parser is running"
echo ""

# Set up environment for position tracking
export DB_ENABLED=true
export POSITION_TRACKING_ENABLED=true
export POSITION_SAMPLE_RATE=0.1
export MATCH_ID=$(date +%s)

echo "Configuration:"
echo "  DB_ENABLED=$DB_ENABLED"
echo "  POSITION_TRACKING_ENABLED=$POSITION_TRACKING_ENABLED"
echo "  POSITION_SAMPLE_RATE=$POSITION_SAMPLE_RATE"
echo "  MATCH_ID=$MATCH_ID"
echo ""

# Parse the replay
echo "Parsing replay..."
curl -X POST -T "$TEST_REPLAY" http://localhost:5600 > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "✓ Replay parsed successfully"
else
    echo "✗ Failed to parse replay"
    exit 1
fi

echo ""
echo "Checking database for position data..."

# Check if we can connect to the database
if [ -z "$DB_HOST" ]; then
    DB_HOST="localhost"
fi

if [ -z "$DB_PORT" ]; then
    DB_PORT="5432"
fi

if [ -z "$DB_NAME" ]; then
    DB_NAME="parser"
fi

if [ -z "$DB_USER" ]; then
    DB_USER="postgres"
fi

# Query for position data
echo ""
echo "Sample queries you can run:"
echo ""
echo "1. Count total position samples:"
echo "   SELECT COUNT(*) FROM position_events WHERE match_id = $MATCH_ID;"
echo ""
echo "2. Get positions for player 0:"
echo "   SELECT time, x, y, life_state FROM position_events WHERE match_id = $MATCH_ID AND slot = 0 ORDER BY time LIMIT 10;"
echo ""
echo "3. Calculate distance traveled:"
echo "   WITH pos_pairs AS ("
echo "     SELECT slot, x, y,"
echo "       LAG(x) OVER (PARTITION BY slot ORDER BY time) as prev_x,"
echo "       LAG(y) OVER (PARTITION BY slot ORDER BY time) as prev_y"
echo "     FROM position_events WHERE match_id = $MATCH_ID"
echo "   )"
echo "   SELECT slot, SUM(SQRT(POWER(x - prev_x, 2) + POWER(y - prev_y, 2))) as distance"
echo "   FROM pos_pairs WHERE prev_x IS NOT NULL GROUP BY slot;"
echo ""
echo "4. View position movement summary:"
echo "   SELECT * FROM position_movement_summary WHERE match_id = $MATCH_ID;"
echo ""

# If psql is available, run some verification queries
if command -v psql &> /dev/null; then
    echo "Running verification queries..."
    
    PGPASSWORD=${DB_PASSWORD:-postgres} psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c \
        "SELECT 
            COUNT(*) as total_samples,
            COUNT(DISTINCT slot) as unique_players,
            MIN(time) as first_time,
            MAX(time) as last_time
         FROM position_events 
         WHERE match_id = $MATCH_ID;" 2>/dev/null
    
    if [ $? -eq 0 ]; then
        echo "✓ Position data successfully stored in database"
    else
        echo "✗ Could not query position data (database may not be available)"
    fi
else
    echo "psql not available - skipping database verification"
    echo "Install PostgreSQL client to run verification queries"
fi

echo ""
echo "=================================="
echo "Test completed!"
echo "=================================="
echo ""
echo "For more examples, see:"
echo "  - docs/POSITION_TRACKING.md"
echo "  - database/migration_examples/V5__Example_position_queries.sql"

