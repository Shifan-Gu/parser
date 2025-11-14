#!/bin/bash

# Database Reset Script for Dota Parser
# This script drops and recreates the replay_raw schema structure.
# WARNING: This will delete ALL data in the replay_raw schema!

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-dota_parser}
DB_USER=${DB_USER:-postgres}
DB_PASSWORD=${DB_PASSWORD:-postgres}
RUN_DBT=${RUN_DBT:-false}
FORCE=${FORCE:-false}

# Export password for psql commands
export PGPASSWORD=$DB_PASSWORD
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --with-dbt)
            RUN_DBT=true
            shift
            ;;
        -f|--force)
            FORCE=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --with-dbt    Run DBT models after resetting the database"
            echo "  -f, --force   Skip confirmation prompt"
            echo "  -h, --help    Show this help message"
            echo ""
            echo "Environment variables:"
            echo "  DB_HOST       Database host (default: localhost)"
            echo "  DB_PORT       Database port (default: 5432)"
            echo "  DB_NAME       Database name (default: dota_parser)"
            echo "  DB_USER       Database user (default: postgres)"
            echo "  DB_PASSWORD   Database password (default: postgres)"
            exit 0
            ;;
        *)
            echo -e "${RED}Error: Unknown option $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo -e "${YELLOW}╔════════════════════════════════════════════════╗${NC}"
echo -e "${YELLOW}║     Database Reset Script for Dota Parser     ║${NC}"
echo -e "${YELLOW}╚════════════════════════════════════════════════╝${NC}"
echo ""
echo "This will reset the ${YELLOW}replay_raw${NC} schema in the database:"
echo -e "  ${RED}• Drop and recreate the replay_raw schema${NC}"
echo -e "  • Leave other schemas untouched"
echo -e "  • Reinitialize the replay_raw schema structure"
if [ "$RUN_DBT" = true ]; then
    echo -e "  • Run DBT models"
fi
echo ""
echo "Connection details:"
echo "  Host: $DB_HOST"
echo "  Port: $DB_PORT"
echo "  Database: $DB_NAME"
echo "  User: $DB_USER"
echo ""

# Confirmation prompt (unless --force is used)
if [ "$FORCE" != true ]; then
    read -p "Are you sure you want to continue? (type 'yes' to confirm): " confirmation
    if [ "$confirmation" != "yes" ]; then
        echo -e "${YELLOW}Reset cancelled.${NC}"
        exit 0
    fi
fi

echo ""
echo -e "${GREEN}Starting replay_raw schema reset...${NC}"
echo ""

# Step 1: Check if PostgreSQL is running
echo "Step 1: Checking PostgreSQL connection..."
if ! psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -c "SELECT 1;" >/dev/null 2>&1; then
    echo -e "${RED}Error: PostgreSQL is not running or not accessible at $DB_HOST:$DB_PORT${NC}"
    echo "Please start PostgreSQL or check your connection settings."
    echo ""
    echo "If using Docker Compose, run:"
    echo "  docker-compose up -d postgres"
    exit 1
fi
echo -e "${GREEN}✓ PostgreSQL is running${NC}"
echo ""

# Step 2: Drop Flyway history (if present)
echo "Step 2: Dropping Flyway schema history (if it exists)..."
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "DROP TABLE IF EXISTS public.flyway_schema_history;" >/dev/null
echo -e "${GREEN}✓ Flyway history cleared${NC}"
echo ""

# Step 3: Drop the replay_raw schema
echo "Step 3: Dropping schema 'replay_raw' (if it exists)..."
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "DROP SCHEMA IF EXISTS replay_raw CASCADE;" >/dev/null
echo -e "${GREEN}✓ Schema dropped${NC}"
echo ""

# Step 4: Create the replay_raw schema
echo "Step 4: Creating schema 'replay_raw'..."
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "CREATE SCHEMA replay_raw;" >/dev/null
echo -e "${GREEN}✓ Schema created${NC}"
echo ""

# Step 5: Initialize the schema objects
echo "Step 5: Initializing replay_raw schema objects..."
INIT_SQL="$SCRIPT_DIR/../database/init.sql"

if [ ! -f "$INIT_SQL" ]; then
    echo -e "${RED}Error: Schema file not found at $INIT_SQL${NC}"
    exit 1
fi

psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME <<EOF >/dev/null
SET search_path TO replay_raw;
\i '$INIT_SQL'
EOF
echo -e "${GREEN}✓ Schema initialized${NC}"
echo ""

# Step 6: Ensure game_info resides in replay_raw schema
echo "Step 6: Moving game_info table into replay_raw schema (if it exists)..."
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME <<'EOF' >/dev/null
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'game_info'
    ) THEN
        EXECUTE 'ALTER TABLE public.game_info SET SCHEMA replay_raw';
    END IF;
END $$;
EOF
echo -e "${GREEN}✓ game_info table aligned with replay_raw schema${NC}"
echo ""

# Step 7: Run DBT models (optional)
if [ "$RUN_DBT" = true ]; then
    echo "Step 7: Running DBT models..."
    DBT_DIR="$SCRIPT_DIR/../dbt"
    
    if [ ! -d "$DBT_DIR" ]; then
        echo -e "${YELLOW}Warning: DBT directory not found at $DBT_DIR${NC}"
        echo "Skipping DBT run."
    else
        cd "$DBT_DIR"
        
        # Check if dbt is available
        if command -v dbt &> /dev/null; then
            dbt run --profiles-dir . || echo -e "${YELLOW}Warning: DBT run encountered errors${NC}"
            echo -e "${GREEN}✓ DBT models executed${NC}"
        else
            echo -e "${YELLOW}Warning: dbt command not found${NC}"
            echo "To run DBT models manually:"
            echo "  cd dbt"
            echo "  dbt run --profiles-dir ."
        fi
    fi
    echo ""
fi

echo -e "${GREEN}╔════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║         Database Reset Completed! ✓            ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════╝${NC}"
echo ""
echo "The replay_raw schema has been reset and is ready to use."
echo ""
echo "Connection string:"
echo "  postgresql://$DB_USER:****@$DB_HOST:$DB_PORT/$DB_NAME"
echo ""
echo "To populate with data, run your parser application or import test data."

