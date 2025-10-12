#!/bin/bash

# Setup script for PostgreSQL database
# This script creates the database and initializes the schema

set -e

# Default values
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5433}
DB_NAME=${DB_NAME:-dota_parser}
DB_USER=${DB_USER:-postgres}
DB_PASSWORD=${DB_PASSWORD:-postgres}

# Export password for psql commands
export PGPASSWORD=$DB_PASSWORD

echo "Setting up PostgreSQL database for Dota Parser..."

# Check if PostgreSQL is running
if ! psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -c "SELECT 1;" >/dev/null 2>&1; then
    echo "Error: PostgreSQL is not running or not accessible at $DB_HOST:$DB_PORT"
    echo "Please start PostgreSQL or check your connection settings."
    exit 1
fi

# Create database if it doesn't exist
echo "Creating database '$DB_NAME' if it doesn't exist..."
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -c "CREATE DATABASE $DB_NAME;" || echo "Database might already exist"

# Run initialization script
echo "Initializing database schema..."
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f database/init.sql

echo "Database setup completed successfully!"
echo "Connection details:"
echo "  Host: $DB_HOST"
echo "  Port: $DB_PORT"
echo "  Database: $DB_NAME"
echo "  User: $DB_USER"

echo ""
echo "To enable database integration, set the following environment variables:"
echo "  export DB_ENABLED=true"
echo "  export DB_HOST=$DB_HOST"
echo "  export DB_PORT=$DB_PORT"
echo "  export DB_NAME=$DB_NAME"
echo "  export DB_USER=$DB_USER"
echo "  export DB_PASSWORD=$DB_PASSWORD"
