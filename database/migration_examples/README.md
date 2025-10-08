# Migration Examples

This directory contains example Flyway migration files to help you create your own database migrations.

## Important Notes

⚠️ **These are examples only!** Do NOT copy them directly to the migration directory unless you actually want to apply these changes.

## Usage

1. Review the example that matches your needs
2. Copy it to `src/main/resources/db/migration/`
3. Rename with the next version number (e.g., V2, V3, etc.)
4. Modify the SQL to match your requirements
5. Test thoroughly before applying to production

## Available Examples

- **V2__Example_add_table.sql** - Shows how to create a new table with indexes
- **V3__Example_add_column.sql** - Shows how to add a column to an existing table
- **V4__Example_create_view.sql** - Shows how to create or update a database view

## Need Help?

See the complete Flyway guide at: `docs/FLYWAY.md`

## Common Migration Patterns

### Creating a Table
```sql
CREATE TABLE IF NOT EXISTS my_table (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Adding a Column
```sql
ALTER TABLE my_table 
ADD COLUMN IF NOT EXISTS new_column VARCHAR(50);
```

### Creating an Index
```sql
CREATE INDEX IF NOT EXISTS idx_my_table_column 
ON my_table(column_name);
```

### Dropping a Column (use with caution!)
```sql
ALTER TABLE my_table 
DROP COLUMN IF EXISTS old_column;
```

### Adding a Foreign Key
```sql
ALTER TABLE child_table
ADD CONSTRAINT fk_parent
FOREIGN KEY (parent_id) 
REFERENCES parent_table(id);
```

## Best Practices

1. Always use `IF NOT EXISTS` or `IF EXISTS` for idempotency
2. Test migrations on a copy of production data first
3. Keep each migration focused on one logical change
4. Add comments to explain complex migrations
5. Never modify a migration that has been applied
6. Include both forward and rollback strategies for complex changes

