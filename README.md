# parser
Replay parse server generating logs from replay files

## Features

* Parse Dota 2 replay files (.dem and .dem.bz2)
* HTTP/HTTPS download support
* **S3 integration** - Download replays directly from S3 buckets (AWS S3, MinIO, etc.)
* Database storage with PostgreSQL
* **Flyway database migrations** - Versioned schema control
* **High-frequency position tracking** - Track hero positions at configurable rates (default 10Hz)
* JSON output with detailed match statistics

## Quickstart

* Run the Java project (it'll start a webserver on port 5600)
* Or build manually with `mvn install`
* Run manually with `java -jar target/stats-0.1.0.jar`
* POST a .dem replay file to the server (example: scripts/test.sh)
* The parser returns line-delimited JSON in the HTTP response

## S3 Support

The parser can download replay files directly from S3 buckets. See [docs/S3.md](docs/S3.md) for detailed setup and usage instructions.

**Quick example:**
```bash
curl "http://localhost:5600/blob?replay_url=s3://bucket-name/path/to/replay.dem"
```

**Configuration:** Set environment variables for S3 access:
- `S3_ENDPOINT` - Custom S3 endpoint (for MinIO/custom S3)
- `S3_REGION` - AWS region (default: us-east-1)
- `S3_ACCESS_KEY` - Access key ID
- `S3_SECRET_KEY` - Secret access key
- `S3_PATH_STYLE_ACCESS` - Use path-style access (true for MinIO)

## Position Tracking

The parser can track hero positions at high frequency (10 times per second by default) and store them in the database. This enables detailed movement analysis, heatmaps, and replay visualization. See [docs/POSITION_TRACKING.md](docs/POSITION_TRACKING.md) for detailed information.

**Configuration:**
- `POSITION_TRACKING_ENABLED` - Enable/disable position tracking (default: true)
- `POSITION_SAMPLE_RATE` - Sample rate in seconds (default: 0.1 = 10Hz)
- `DB_ENABLED` - Must be true for position tracking to work

**Example:**
```bash
export DB_ENABLED=true
export POSITION_TRACKING_ENABLED=true
export POSITION_SAMPLE_RATE=0.1
java -jar target/stats-0.1.0.jar
```

## Development

### With Docker Compose (Recommended)

```bash
# Start all services (parser, PostgreSQL, MinIO)
docker-compose -f docker-compose.dev.yml up

# Run tests
bash scripts/test/test_parser.sh      # Basic parser test
bash scripts/test/test_s3.sh          # S3 integration test
bash scripts/test/test_database.sh    # Database test
```

### Manual Build

```bash
mvn clean install
java -jar target/stats-0.1.0.jar
```

## Project Structure

```
parser/
├── docs/                       # Documentation
│   ├── S3.md                  # S3 setup guide
│   ├── DATABASE.md            # Database guide
│   ├── FLYWAY.md              # Migration guide
│   ├── POSITION_TRACKING.md   # Position tracking guide
│   ├── TESTING.md             # Testing guide
│   └── TEST_RESULTS.md        # Test results
├── scripts/                   # Utility scripts
│   ├── setup/                # Setup scripts
│   ├── build/                # Build scripts
│   ├── test/                 # Test scripts
│   └── query/                # Query scripts
├── src/                       # Java source code
│   └── main/resources/
│       └── db/migration/     # Flyway migrations
├── processors/                # Node.js processors
└── database/                  # Database schema & examples
```

## Documentation

- [docs/S3.md](docs/S3.md) - S3 setup and configuration guide
- [docs/DATABASE.md](docs/DATABASE.md) - Database setup and usage
- [docs/FLYWAY.md](docs/FLYWAY.md) - Database migration guide
- [docs/POSITION_TRACKING.md](docs/POSITION_TRACKING.md) - Position tracking and movement analysis
- [docs/TESTING.md](docs/TESTING.md) - Comprehensive testing guide
- [scripts/README.md](scripts/README.md) - Scripts documentation