-- Create constants table for hero ID to Chinese name mapping in dota_constants schema

CREATE SCHEMA IF NOT EXISTS dota_constants;

CREATE TABLE IF NOT EXISTS dota_constants.hero_chinese_names (
    hero_id INTEGER PRIMARY KEY,
    chinese_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on chinese_name for reverse lookups if needed
CREATE INDEX IF NOT EXISTS idx_hero_chinese_names_chinese_name ON dota_constants.hero_chinese_names(chinese_name);

-- Add comment to table
COMMENT ON TABLE dota_constants.hero_chinese_names IS 'Constants table mapping hero IDs to their Chinese names';

-- Add comments to columns
COMMENT ON COLUMN dota_constants.hero_chinese_names.hero_id IS 'Numeric identifier for the hero';
COMMENT ON COLUMN dota_constants.hero_chinese_names.chinese_name IS 'Chinese name of the hero';
COMMENT ON COLUMN dota_constants.hero_chinese_names.created_at IS 'Timestamp when the record was created';
COMMENT ON COLUMN dota_constants.hero_chinese_names.updated_at IS 'Timestamp when the record was last updated';

