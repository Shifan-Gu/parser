# Flyway Database Migration Guide

This project uses [Flyway](https://flywaydb.org/) for database migration control. Flyway provides version control for your database schema, making it easy to track changes and maintain consistency across environments.

## Overview

Flyway manages database migrations automatically when the application starts. It tracks which migrations have been applied and applies any pending migrations in order.

### Key Features

- **Automatic Migration**: Migrations run automatically when the application starts
- **Version Control**: Each migration is versioned and tracked
- **Idempotent**: Safe to run multiple times - already applied migrations are skipped
- **Baseline Support**: Existing databases can be baselined to start versioning

## Migration File Location

Migration files are located in:
```
src/main/resources/db/migration/
```

## Naming Convention

Migration files must follow this naming pattern:
```
V{version}__{description}.sql
```

Examples:
- `V1__Initial_schema.sql` - Initial database schema
- `V2__Add_user_table.sql` - Add a new user table
- `V3__Add_index_to_events.sql` - Add an index
- `V2.1__Add_column_to_users.sql` - Add a column (version 2.1)

### Version Numbering

- Versions are sorted numerically
- Use integers for major changes: `V1`, `V2`, `V3`
- Use decimals for minor changes: `V2.1`, `V2.2`
- Versions must be unique and sequential

## Current Migrations

### V1__Initial_schema.sql

The initial migration creates:
- All event tables (combat_log_events, action_events, ping_events, etc.)
- Indexes for performance optimization
- Views for common queries (match_summary, player_stats)
- PostgreSQL extensions (uuid-ossp)

## How It Works

### Application Startup

When the application starts (Parse.java constructor):
1. Database connection is established
2. DatabaseInitializer.initializeDatabase() is called
3. Flyway checks the database schema version
4. Pending migrations are applied in order
5. Migration history is recorded in `flyway_schema_history` table

### Flyway Configuration

Configuration in `DatabaseInitializer.java`:
```java
Flyway flyway = Flyway.configure()
    .dataSource(jdbcUrl, username, password)
    .locations("classpath:db/migration")
    .baselineOnMigrate(true)  // Handle existing databases
    .baselineVersion("0")      // Start baseline at version 0
    .validateOnMigrate(true)   // Validate migrations
    .load();
```

## Creating New Migrations

### Step 1: Create Migration File

Create a new SQL file in `src/main/resources/db/migration/`:
```bash
touch src/main/resources/db/migration/V2__Add_performance_table.sql
```

### Step 2: Write Migration SQL

Example migration to add a new table:
```sql
-- V2__Add_performance_table.sql
CREATE TABLE IF NOT EXISTS performance_metrics (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value REAL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_performance_match_id 
    ON performance_metrics(match_id);
```

### Step 3: Test Migration

The migration will be applied automatically on next application start:
```bash
docker-compose down
docker-compose up --build
```

### Step 4: Verify Migration

Check application logs for migration messages:
```
Current database version: 1
Found 1 pending migration(s).
Successfully applied 1 migration(s).
```

## Migration Best Practices

### DO

✅ **Use IF NOT EXISTS** for idempotency:
```sql
CREATE TABLE IF NOT EXISTS my_table (...);
CREATE INDEX IF NOT EXISTS idx_name ON my_table(column);
```

✅ **One logical change per migration**:
- V2__Add_user_table.sql
- V3__Add_user_indexes.sql

✅ **Test migrations on a copy of production data**

✅ **Write migrations that can handle existing data**:
```sql
ALTER TABLE users ADD COLUMN email VARCHAR(255);
UPDATE users SET email = CONCAT(username, '@example.com') WHERE email IS NULL;
```

✅ **Add indexes for new columns that will be queried**

### DON'T

❌ **Never modify an applied migration file**
- Create a new migration instead

❌ **Don't use DROP TABLE without careful consideration**
- Data loss is permanent

❌ **Avoid complex transactions in migrations**
- Keep migrations simple and focused

❌ **Don't skip version numbers**
- Migrations must be sequential

## Checking Migration Status

### Via Application Logs

When the application starts, Flyway logs:
- Current database version
- Number of pending migrations
- Number of applied migrations

### Via Database

Query the Flyway schema history table:
```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

### Via Docker

Check the application logs:
```bash
docker logs dota_parser_app
```

## Troubleshooting

### Migration Failed

If a migration fails:
1. Check the error in application logs
2. Fix the migration file
3. Manually clean up any partial changes in database
4. Delete the failed entry from `flyway_schema_history`
5. Restart the application

### Baseline Existing Database

For databases created before Flyway was added:
```java
// Already configured in DatabaseInitializer.java
.baselineOnMigrate(true)
.baselineVersion("0")
```

This tells Flyway to baseline existing databases at version 0, then apply all migrations starting from V1.

### Reset Database (Development Only)

To reset the database completely:
```bash
docker-compose down -v  # Remove volumes
docker-compose up --build
```

⚠️ **WARNING**: This deletes all data!

## Environment Variables

Flyway uses the same database connection settings:
- `DB_HOST` (default: localhost)
- `DB_PORT` (default: 5432)
- `DB_NAME` (default: dota_parser)
- `DB_USER` (default: postgres)
- `DB_PASSWORD` (default: postgres)

## Advanced Features

### Repeatable Migrations

For views, procedures, or functions that should be reapplied:
```
R__Create_views.sql
```

Repeatable migrations run after versioned migrations whenever their checksum changes.

### Java-based Migrations

For complex migrations requiring Java code, implement `JavaMigration`:
```java
public class V2__Complex_data_migration implements JavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        // Java migration code
    }
}
```

### Callbacks

Flyway supports callbacks for custom logic:
- `beforeMigrate.sql`
- `afterMigrate.sql`
- etc.

## CI/CD Integration

### Automated Testing

Test migrations in CI/CD pipeline:
```bash
# Start test database
docker-compose -f docker-compose.dev.yml up -d postgres

# Run application (migrations will apply)
docker-compose -f docker-compose.dev.yml up parser

# Run tests
./scripts/test/test_database.sh
```

### Production Deployment

1. Test migrations on staging environment
2. Backup production database
3. Deploy new application version
4. Migrations apply automatically on startup
5. Verify migration success in logs

## Resources

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Flyway SQL Migrations](https://flywaydb.org/documentation/concepts/migrations#sql-based-migrations)
- [Flyway Best Practices](https://flywaydb.org/documentation/concepts/migrations#best-practices)

## Support

For issues or questions:
1. Check application logs for migration errors
2. Review Flyway documentation
3. Check `flyway_schema_history` table for migration status
4. Review migration files in `src/main/resources/db/migration/`

