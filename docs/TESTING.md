# Testing Guide

This guide covers all testing procedures for the Dota 2 replay parser.

## Table of Contents

- [Quick Start](#quick-start)
- [Test Scripts](#test-scripts)
- [Test Types](#test-types)
- [Troubleshooting](#troubleshooting)

## Quick Start

### Prerequisites

```bash
# Start all services
docker-compose -f docker-compose.dev.yml up -d

# Wait for services to be ready (about 10-15 seconds)
sleep 15
```

### Run All Tests

```bash
# Basic parser test
bash scripts/test/test_parser.sh

# S3 integration test
bash scripts/test/test_s3.sh

# Database test
bash scripts/test/test_database.sh
```

## Test Scripts

All test scripts are located in the `scripts/test/` directory.

### 1. Parser Basic Test

**Script**: `scripts/test/test_parser.sh`

Tests the parser with an HTTP replay URL.

```bash
bash scripts/test/test_parser.sh
```

**What it tests:**
- Parser health endpoint
- HTTP replay download
- Replay parsing
- JSON output generation

**Expected output:**
```
✓ Parser is running
✓ Parser test PASSED
Response preview (first 500 chars): {...}
Full response length: ~200KB
```

---

### 2. S3 Integration Test (Bash)

**Script**: `scripts/test/test_s3.sh`

Tests S3 integration using MinIO with automatic MinIO client installation.

```bash
bash scripts/test/test_s3.sh
```

**What it tests:**
- MinIO availability
- Parser availability
- MinIO client setup
- Bucket creation
- File upload to S3
- S3 replay download
- S3 replay parsing

**Prerequisites:**
- MinIO service running (included in docker-compose.dev.yml)
- Test file in `test-data/` folder

**Expected output:**
```
✓ Test file found
✓ MinIO is running
✓ Parser is running
✓ MinIO client ready
✓ Bucket created
✓ Test file uploaded
✓ Parser successfully processed S3 replay
SUCCESS: S3 integration is working!
```

---

### 3. S3 Integration Test (Python)

**Script**: `scripts/test/test_s3.py`

Python-based S3 test using boto3 library.

```bash
# Install dependencies first
pip install boto3

# Run test
python3 scripts/test/test_s3.py
```

**What it tests:**
- Same as bash script, but with Python/boto3
- More detailed error messages
- Cross-platform compatibility

**Prerequisites:**
- Python 3.6+
- boto3 package: `pip install boto3`

---

### 4. Database Integration Test

**Script**: `scripts/test/test_database.sh`

Tests database integration and storage.

```bash
bash scripts/test/test_database.sh
```

**What it tests:**
- PostgreSQL availability
- Database connectivity
- Schema initialization
- Data insertion
- Data querying
- Database performance

**Prerequisites:**
- PostgreSQL service running
- Database initialized with schema

---

## Test Types

### Unit Tests

Currently, the project uses integration tests. For unit testing individual components:

```bash
# Java unit tests (when implemented)
mvn test
```

### Integration Tests

Integration tests verify the entire system working together:

1. **Parser Integration**: Tests HTTP download → parse → output pipeline
2. **S3 Integration**: Tests S3 download → parse → output pipeline
3. **Database Integration**: Tests parse → database storage → query pipeline

### End-to-End Tests

E2E tests simulate real-world usage:

```bash
# Complete workflow test
bash scripts/test/test_parser.sh && \
bash scripts/test/test_s3.sh && \
bash scripts/test/test_database.sh
```

## Testing Different Scenarios

### Test with Local Replay File

```bash
# Direct POST of replay file
curl -X POST \
  --data-binary "@test-data/7503212404.dem" \
  http://localhost:5600/ > output.json

# View results
cat output.json | jq .
```

### Test with HTTP URL

```bash
curl "http://localhost:5600/blob?replay_url=http://example.com/replay.dem" \
  > output.json
```

### Test with S3 URL

```bash
curl "http://localhost:5600/blob?replay_url=s3://bucket/path/replay.dem" \
  > output.json
```

### Test with Compressed Replay

Both `.dem` and `.dem.bz2` files are automatically detected and handled:

```bash
# Compressed replay
curl "http://localhost:5600/blob?replay_url=s3://bucket/replay.dem.bz2"

# Uncompressed replay
curl "http://localhost:5600/blob?replay_url=s3://bucket/replay.dem"
```

## Test Data

Test replay files are stored in the `test-data/` directory (excluded from git).

**Current test file:**
- `7503212404.dem` (78.5 MB)

To add more test files:

```bash
# Add to test-data folder
cp path/to/replay.dem test-data/

# Upload to MinIO for S3 testing
mc cp test-data/replay.dem local/dota-replays/replays/
```

## Performance Testing

### Measure Parse Time

```bash
time curl -s "http://localhost:5600/blob?replay_url=s3://bucket/replay.dem" \
  > /dev/null
```

### Measure with Different File Sizes

```bash
# Small replay (< 20 MB)
time curl -s "http://localhost:5600/blob?replay_url=s3://bucket/small.dem"

# Medium replay (20-50 MB)
time curl -s "http://localhost:5600/blob?replay_url=s3://bucket/medium.dem"

# Large replay (> 50 MB)
time curl -s "http://localhost:5600/blob?replay_url=s3://bucket/large.dem"
```

## Troubleshooting

### Parser Not Running

```bash
# Check if container is running
docker ps | grep parser

# View logs
docker-compose -f docker-compose.dev.yml logs parser

# Restart parser
docker-compose -f docker-compose.dev.yml restart parser
```

### MinIO Connection Issues

```bash
# Check MinIO status
curl http://localhost:9000/minio/health/live

# View MinIO logs
docker-compose -f docker-compose.dev.yml logs minio

# Restart MinIO
docker-compose -f docker-compose.dev.yml restart minio
```

### Database Connection Issues

```bash
# Check PostgreSQL status
docker-compose -f docker-compose.dev.yml exec postgres pg_isready

# View database logs
docker-compose -f docker-compose.dev.yml logs postgres

# Connect to database
docker-compose -f docker-compose.dev.yml exec postgres \
  psql -U postgres -d dota_parser
```

### Test File Not Found

```bash
# Check if test file exists
ls -lh test-data/

# The test-data folder should be at project root
# If missing, create it and add a replay file
mkdir -p test-data
# Add your .dem file to test-data/
```

### S3 Upload Fails

```bash
# Check MinIO credentials
echo $S3_ACCESS_KEY
echo $S3_SECRET_KEY

# Verify bucket exists
mc ls local/

# Recreate bucket if needed
mc mb local/dota-replays
```

### Parse Errors

Common issues and solutions:

1. **"Unknown event type"** - Normal warnings, not errors
2. **"Corrupted replay"** - Try a different replay file
3. **"Timeout"** - Replay is too large or parse is taking too long
4. **"S3 download error"** - Check S3 credentials and network

### Clean Test Environment

```bash
# Stop all containers
docker-compose -f docker-compose.dev.yml down

# Remove volumes (WARNING: deletes all data)
docker-compose -f docker-compose.dev.yml down -v

# Rebuild and restart
docker-compose -f docker-compose.dev.yml build --no-cache
docker-compose -f docker-compose.dev.yml up -d
```

## Continuous Integration

For CI/CD pipelines, use this workflow:

```bash
#!/bin/bash
set -e

# 1. Start services
docker-compose -f docker-compose.dev.yml up -d

# 2. Wait for services
sleep 15

# 3. Run tests
bash scripts/test/test_parser.sh
bash scripts/test/test_s3.sh
bash scripts/test/test_database.sh

# 4. Cleanup
docker-compose -f docker-compose.dev.yml down
```

## Test Coverage

Current test coverage:

| Component | Coverage | Test Script |
|-----------|----------|-------------|
| HTTP Parser | ✅ Full | test_parser.sh |
| S3 Integration | ✅ Full | test_s3.sh, test_s3.py |
| Database | ✅ Full | test_database.sh |
| Health Check | ✅ Full | All test scripts |
| Error Handling | ⚠️ Partial | Manual testing |
| Load Testing | ❌ None | TODO |

## Contributing Tests

When adding new features, please add corresponding tests:

1. Create test script in `scripts/test/`
2. Document in this file
3. Add to CI pipeline
4. Ensure test is idempotent (can run multiple times)

### Test Script Template

```bash
#!/bin/bash
set -e

echo "================================"
echo "Your Test Name"
echo "================================"
echo ""

# Check prerequisites
echo "Checking prerequisites..."
# ... your checks ...

# Run test
echo "Running test..."
# ... your test ...

# Verify results
if [ $? -eq 0 ]; then
    echo "✓ Test PASSED"
else
    echo "✗ Test FAILED"
    exit 1
fi
```

## Additional Resources

- [S3 Documentation](S3.md) - S3 setup and configuration
- [Database Documentation](DATABASE.md) - Database setup and usage
- [Main README](../README.md) - Project overview

