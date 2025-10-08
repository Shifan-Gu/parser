# Scripts Directory

This directory contains all utility scripts for the Dota 2 replay parser project, organized by category.

## Directory Structure

```
scripts/
├── README.md           # This file
├── setup/              # Setup and initialization scripts
├── build/              # Build and compilation scripts
├── test/               # Testing scripts
└── query/              # Database query scripts
```

## Setup Scripts

Located in `setup/`

### setup_database.sh
Initializes the PostgreSQL database schema and creates necessary tables.

```bash
bash scripts/setup/setup_database.sh
```

## Build Scripts

Located in `build/`

### rebuild.sh
Completely rebuilds the project from scratch.

```bash
bash scripts/build/rebuild.sh
```

### postbuild.sh
Post-build tasks and optimizations.

```bash
bash scripts/build/postbuild.sh
```

### update_proto.sh
Updates protocol buffer definitions.

```bash
bash scripts/build/update_proto.sh
```

### docker_helper.sh
Docker utility functions and helpers.

```bash
bash scripts/build/docker_helper.sh
```

## Test Scripts

Located in `test/` - See [TESTING.md](../docs/TESTING.md) for detailed documentation.

### test_parser.sh
Tests basic parser functionality with HTTP replay URLs.

```bash
bash scripts/test/test_parser.sh
```

### test_s3.sh
Tests S3 integration with MinIO (bash version).

```bash
bash scripts/test/test_s3.sh
```

### test_s3.py
Tests S3 integration with MinIO (Python version, requires boto3).

```bash
python3 scripts/test/test_s3.py
```

### test_database.sh
Tests database integration and storage.

```bash
bash scripts/test/test_database.sh
```

## Query Scripts

Located in `query/`

### query_database.sh
Interactive database query tool.

```bash
bash scripts/query/query_database.sh
```

## Usage Examples

### Complete Setup

```bash
# 1. Start services
docker-compose -f docker-compose.dev.yml up -d

# 2. Setup database
bash scripts/setup/setup_database.sh

# 3. Run all tests
bash scripts/test/test_parser.sh
bash scripts/test/test_s3.sh
bash scripts/test/test_database.sh
```

### Development Workflow

```bash
# Rebuild after code changes
bash scripts/build/rebuild.sh

# Test changes
bash scripts/test/test_parser.sh

# Query results
bash scripts/query/query_database.sh
```

### CI/CD Pipeline

```bash
#!/bin/bash
set -e

# Build
docker-compose -f docker-compose.dev.yml build

# Start services
docker-compose -f docker-compose.dev.yml up -d

# Wait for services to be ready
sleep 15

# Run tests
bash scripts/test/test_parser.sh
bash scripts/test/test_s3.sh
bash scripts/test/test_database.sh

# Cleanup
docker-compose -f docker-compose.dev.yml down
```

## Making Scripts Executable

All scripts should be executable. If you encounter permission issues:

```bash
# Make all scripts executable
find scripts/ -name "*.sh" -type f -exec chmod +x {} \;
find scripts/ -name "*.py" -type f -exec chmod +x {} \;
```

## Adding New Scripts

When adding new scripts:

1. Place in appropriate category directory
2. Add shebang line (`#!/bin/bash` or `#!/usr/bin/env python3`)
3. Make executable: `chmod +x scripts/category/script.sh`
4. Document in this README
5. Add detailed docs to [TESTING.md](../docs/TESTING.md) if it's a test script

### Script Template

```bash
#!/bin/bash

# Script Name: descriptive_name.sh
# Description: What this script does
# Usage: bash scripts/category/descriptive_name.sh

set -e  # Exit on error

echo "================================"
echo "Script Name"
echo "================================"
echo ""

# Your script logic here

echo ""
echo "================================"
echo "Script completed successfully"
echo "================================"
```

## Troubleshooting

### Script Not Found

```bash
# Check if file exists
ls -l scripts/category/script.sh

# Check current directory
pwd  # Should be project root
```

### Permission Denied

```bash
# Make script executable
chmod +x scripts/category/script.sh
```

### Command Not Found

Some scripts require specific tools:

- **MinIO client (mc)**: Auto-installed by test_s3.sh
- **boto3**: `pip install boto3` (for Python S3 tests)
- **jq**: `brew install jq` or `apt-get install jq`
- **curl**: Usually pre-installed
- **docker-compose**: Required for all Docker operations

## Additional Resources

- [Testing Guide](../docs/TESTING.md) - Comprehensive testing documentation
- [S3 Documentation](../docs/S3.md) - S3 setup and usage
- [Database Documentation](../docs/DATABASE.md) - Database guide
- [Main README](../README.md) - Project overview

