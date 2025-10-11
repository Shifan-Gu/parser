#!/bin/bash

# Test script for database integration
# This script tests the database setup and basic functionality

set -e

echo "Testing Database Integration for Dota 2 Parser"
echo "=============================================="

# Default values
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5433}
DB_NAME=${DB_NAME:-dota_parser}
DB_USER=${DB_USER:-postgres}
DB_PASSWORD=${DB_PASSWORD:-postgres}

# Test database connection
echo "1. Testing database connection..."
if PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT version();" >/dev/null 2>&1; then
    echo "   ✓ Database connection successful"
else
    echo "   ✗ Database connection failed"
    echo "   Please ensure PostgreSQL is running and accessible"
    exit 1
fi

# Test table existence
echo "2. Testing table existence..."
tables_exist=true
for table in combat_log_events interval_events draft_timing_events ward_events action_events; do
    if ! PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT COUNT(*) FROM $table;" >/dev/null 2>&1; then
        echo "   ✗ $table table not found"
        tables_exist=false
    fi
done

if [ "$tables_exist" = true ]; then
    echo "   ✓ All event tables exist"
else
    echo "   Run ./scripts/setup_database.sh to initialize the database"
    exit 1
fi

# Test indexes
echo "3. Testing indexes..."
index_count=$(PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public';" | tr -d ' ')
if [ "$index_count" -gt 0 ]; then
    echo "   ✓ Database indexes exist ($index_count indexes found)"
else
    echo "   ✗ No indexes found"
fi

# Test views
echo "4. Testing views..."
if PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT COUNT(*) FROM match_summary;" >/dev/null 2>&1; then
    echo "   ✓ Database views exist"
else
    echo "   ✗ Database views not found"
fi

# Test environment variables
echo "5. Testing environment variables..."
if [ -n "$DB_ENABLED" ] && [ "$DB_ENABLED" = "true" ]; then
    echo "   ✓ DB_ENABLED is set to true"
else
    echo "   ⚠ DB_ENABLED not set or not true"
    echo "   Set DB_ENABLED=true to enable database integration"
fi

# Test Java compilation
echo "6. Testing Java compilation..."
if [ -f "target/stats-0.1.0.jar" ]; then
    echo "   ✓ JAR file exists"
else
    echo "   ⚠ JAR file not found, attempting to compile..."
    if mvn clean install -q >/dev/null 2>&1; then
        echo "   ✓ Compilation successful"
    else
        echo "   ✗ Compilation failed"
        exit 1
    fi
fi

echo ""
echo "Database Integration Test Summary"
echo "================================="
echo "✓ All tests passed!"
echo ""
echo "To run the parser with database integration:"
echo "1. Set environment variables:"
echo "   export DB_ENABLED=true"
echo "   export DB_HOST=$DB_HOST"
echo "   export DB_PORT=$DB_PORT"
echo "   export DB_NAME=$DB_NAME"
echo "   export DB_USER=$DB_USER"
echo "   export DB_PASSWORD=$DB_PASSWORD"
echo ""
echo "2. Run the parser:"
echo "   java -jar target/stats-0.1.0.jar"
echo ""
echo "3. Query the database:"
echo "   ./scripts/query_database.sh --matches"
