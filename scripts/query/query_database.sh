#!/bin/bash

# Query script for PostgreSQL database
# This script provides common queries for analyzing parsed data

set -e

# Default values
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5433}
DB_NAME=${DB_NAME:-dota_parser}
DB_USER=${DB_USER:-postgres}
DB_PASSWORD=${DB_PASSWORD:-postgres}

# Function to execute SQL query
execute_query() {
    local query="$1"
    echo "Executing query:"
    echo "$query"
    echo "----------------------------------------"
    PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "$query"
    echo ""
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTION]"
    echo ""
    echo "Options:"
    echo "  --matches           Show all matches"
    echo "  --events COUNT      Show recent events (default: 10)"
    echo "  --match-id ID       Show events for specific match ID"
    echo "  --player-stats      Show player statistics"
    echo "  --hero-stats        Show hero statistics"
    echo "  --combat-events     Show combat log events"
    echo "  --custom SQL        Execute custom SQL query"
    echo ""
    echo "Examples:"
    echo "  $0 --matches"
    echo "  $0 --events 50"
    echo "  $0 --match-id 12345"
    echo "  $0 --custom \"SELECT * FROM game_events WHERE type='interval' LIMIT 5;\""
}

# Parse command line arguments
case "${1:-}" in
    --matches)
        execute_query "SELECT match_id, COUNT(*) as events, MIN(time) as start_time, MAX(time) as end_time, created_at FROM game_events GROUP BY match_id, created_at ORDER BY created_at DESC;"
        ;;
    --events)
        count=${2:-10}
        execute_query "SELECT match_id, time, type, slot, hero_id, key, value FROM game_events ORDER BY id DESC LIMIT $count;"
        ;;
    --match-id)
        match_id=${2:-}
        if [ -z "$match_id" ]; then
            echo "Error: Match ID required for --match-id option"
            exit 1
        fi
        execute_query "SELECT time, type, slot, hero_id, key, value FROM game_events WHERE match_id=$match_id ORDER BY time;"
        ;;
    --player-stats)
        execute_query "SELECT match_id, slot, hero_id, MAX(level) as max_level, MAX(gold) as max_gold, MAX(kills) as kills, MAX(deaths) as deaths, MAX(assists) as assists FROM game_events WHERE type='interval' AND slot IS NOT NULL GROUP BY match_id, slot, hero_id ORDER BY match_id, slot;"
        ;;
    --hero-stats)
        execute_query "SELECT hero_id, COUNT(*) as games_played, AVG(MAX(level)) as avg_max_level, AVG(MAX(kills)) as avg_kills, AVG(MAX(deaths)) as avg_deaths FROM (SELECT match_id, slot, hero_id, MAX(level) as level, MAX(kills) as kills, MAX(deaths) as deaths FROM game_events WHERE type='interval' AND hero_id IS NOT NULL GROUP BY match_id, slot, hero_id) as player_stats GROUP BY hero_id ORDER BY games_played DESC;"
        ;;
    --combat-events)
        execute_query "SELECT match_id, time, type, attackername, targetname, value FROM game_events WHERE type LIKE 'DOTA_COMBATLOG_%' ORDER BY match_id, time DESC LIMIT 20;"
        ;;
    --custom)
        custom_query=${2:-}
        if [ -z "$custom_query" ]; then
            echo "Error: Custom SQL query required for --custom option"
            exit 1
        fi
        execute_query "$custom_query"
        ;;
    --help|-h)
        show_usage
        ;;
    *)
        echo "Error: Unknown option '$1'"
        show_usage
        exit 1
        ;;
esac
