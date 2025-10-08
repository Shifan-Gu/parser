# Documentation Index

Welcome to the Dota 2 Replay Parser documentation. This directory contains comprehensive guides for all aspects of the parser.

## Quick Navigation

### Getting Started
- [Main README](../README.md) - Project overview and quick start
- [TESTING.md](TESTING.md) - Testing guide and procedures

### Features
- [S3.md](S3.md) - S3/MinIO integration guide
- [DATABASE.md](DATABASE.md) - PostgreSQL database setup and usage

### Results
- [TEST_RESULTS.md](TEST_RESULTS.md) - Latest test results and verification

## Documentation Overview

### [S3.md](S3.md)
**S3 Integration Guide**

Everything you need to know about using S3 (or S3-compatible services like MinIO) for replay storage:

- Local development with MinIO
- Production deployment with AWS S3
- Configuration and environment variables
- IAM policies and security
- Troubleshooting common issues
- Performance considerations

**Use this when:**
- Setting up S3 integration
- Deploying to AWS
- Configuring MinIO locally
- Troubleshooting S3 connections

---

### [DATABASE.md](DATABASE.md)
**Database Integration Guide**

PostgreSQL database setup, schema, and usage:

- Database schema initialization
- Connection configuration
- Query examples
- Performance optimization
- Backup and restore procedures

**Use this when:**
- Setting up the database
- Understanding the schema
- Querying match data
- Optimizing database performance

---

### [TESTING.md](TESTING.md)
**Comprehensive Testing Guide**

All testing procedures and test scripts:

- Test script documentation
- Running different test types
- Integration testing
- Performance testing
- Troubleshooting test failures
- CI/CD integration

**Use this when:**
- Running tests
- Adding new tests
- Setting up CI/CD
- Debugging test failures

---

### [TEST_RESULTS.md](TEST_RESULTS.md)
**Test Results and Verification**

Latest test execution results and verification:

- S3 integration test results
- Performance metrics
- Configuration examples
- Success criteria
- Known issues

**Use this when:**
- Verifying features work
- Checking performance baselines
- Comparing test results

---

## Quick Links

### Common Tasks

| Task | Documentation | Script |
|------|---------------|--------|
| Start services | [Main README](../README.md) | `docker-compose up` |
| Test parser | [TESTING.md](TESTING.md) | `scripts/test/test_parser.sh` |
| Test S3 | [S3.md](S3.md) | `scripts/test/test_s3.sh` |
| Test database | [DATABASE.md](DATABASE.md) | `scripts/test/test_database.sh` |
| Setup database | [DATABASE.md](DATABASE.md) | `scripts/setup/setup_database.sh` |
| Query database | [DATABASE.md](DATABASE.md) | `scripts/query/query_database.sh` |

### Configuration

| Feature | Environment Variable | Documentation |
|---------|---------------------|---------------|
| S3 Endpoint | `S3_ENDPOINT` | [S3.md](S3.md) |
| S3 Region | `S3_REGION` | [S3.md](S3.md) |
| S3 Access Key | `S3_ACCESS_KEY` | [S3.md](S3.md) |
| S3 Secret Key | `S3_SECRET_KEY` | [S3.md](S3.md) |
| DB Host | `DB_HOST` | [DATABASE.md](DATABASE.md) |
| DB Port | `DB_PORT` | [DATABASE.md](DATABASE.md) |
| DB Name | `DB_NAME` | [DATABASE.md](DATABASE.md) |

### API Endpoints

| Endpoint | Purpose | Documentation |
|----------|---------|---------------|
| `POST /` | Parse replay (direct upload) | [Main README](../README.md) |
| `GET /blob` | Parse replay (URL/S3) | [S3.md](S3.md) |
| `GET /healthz` | Health check | [TESTING.md](TESTING.md) |

## Project Structure

```
parser/
├── docs/                    # This directory
│   ├── README.md           # This file
│   ├── S3.md               # S3 integration guide
│   ├── DATABASE.md         # Database guide
│   ├── TESTING.md          # Testing guide
│   └── TEST_RESULTS.md     # Test results
│
├── scripts/                 # Utility scripts
│   ├── README.md           # Scripts documentation
│   ├── setup/              # Setup scripts
│   ├── build/              # Build scripts
│   ├── test/               # Test scripts
│   └── query/              # Database query scripts
│
├── src/                     # Java source code
│   └── main/java/opendota/
│       ├── Main.java       # HTTP server & routing
│       ├── Parse.java      # Replay parser
│       ├── S3Service.java  # S3 integration
│       └── database/       # Database layer
│
├── processors/              # Node.js data processors
├── database/               # SQL schema files
└── test-data/              # Test replay files (excluded from git)
```

## Development Workflow

### 1. Initial Setup
```bash
# Clone and start services
docker-compose -f docker-compose.dev.yml up

# Setup database (if needed)
bash scripts/setup/setup_database.sh
```

### 2. Development Cycle
```bash
# Make code changes...

# Rebuild
docker-compose -f docker-compose.dev.yml build

# Restart
docker-compose -f docker-compose.dev.yml restart parser

# Test
bash scripts/test/test_parser.sh
```

### 3. Testing
```bash
# Run all tests
bash scripts/test/test_parser.sh
bash scripts/test/test_s3.sh
bash scripts/test/test_database.sh
```

### 4. Deployment
See [S3.md](S3.md) and [DATABASE.md](DATABASE.md) for production deployment guides.

## Troubleshooting

### Common Issues

| Issue | Solution | Documentation |
|-------|----------|---------------|
| Parser not starting | Check logs, rebuild | [TESTING.md](TESTING.md#troubleshooting) |
| S3 connection fails | Check credentials, endpoint | [S3.md](S3.md#troubleshooting) |
| Database connection fails | Check PostgreSQL status | [DATABASE.md](DATABASE.md) |
| Tests fail | See troubleshooting guide | [TESTING.md](TESTING.md#troubleshooting) |

### Getting Help

1. Check the relevant documentation above
2. Review [TESTING.md](TESTING.md) troubleshooting section
3. Check Docker logs: `docker-compose logs parser`
4. Review test results: [TEST_RESULTS.md](TEST_RESULTS.md)

## Contributing

When contributing, please:

1. Update relevant documentation
2. Add tests for new features (see [TESTING.md](TESTING.md))
3. Run all tests before submitting
4. Update [TEST_RESULTS.md](TEST_RESULTS.md) with new test results

## Additional Resources

### External Documentation
- [Clarity Parser](https://github.com/skadistats/clarity) - Dota 2 replay parser library
- [AWS S3 Documentation](https://docs.aws.amazon.com/s3/)
- [MinIO Documentation](https://min.io/docs/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

### Related Projects
- [OpenDota](https://www.opendota.com/) - Dota 2 statistics platform
- [Dota 2 API](https://wiki.teamfortress.com/wiki/WebAPI) - Official Valve API

## Document History

| Date | Change | Author |
|------|--------|--------|
| 2025-10-08 | Documentation reorganization | System |
| 2025-10-08 | Added S3 integration docs | System |
| 2025-10-08 | Added comprehensive testing guide | System |
| 2025-10-08 | Created documentation index | System |

