# parser
Replay parse server generating logs from replay files with optional async job control

## Features

* Parse Dota 2 replay files (.dem and .dem.bz2)
* HTTP/HTTPS download support
* **S3 integration** - Download replays directly from S3 buckets (AWS S3, MinIO, etc.)
* Database storage with PostgreSQL
* **Flyway database migrations** - Versioned schema control
* **Async replay jobs** - Queue remote URLs or local files and poll for status/results
* JSON output with detailed match statistics

## Quickstart

* Run the Java project (it'll start a webserver on port 5600)
* Or build manually with `mvn install`
* Run manually with `java -jar target/stats-0.1.0.jar`
* Submit a replay processing job via the `/replay/jobs` endpoint (see below)
* Poll the job for completion or visit the HTML dashboard at `http://localhost:5600/`

## API Documentation

Interactive Swagger UI is available once the server is running:

```bash
open "http://localhost:5600/swagger"
```

The generated OpenAPI description is also exposed at `http://localhost:5600/swagger/openapi.json`.

## Replay Job Queue

The parser now supports asynchronous replay processing. Jobs can be created from remote URLs (HTTP/S or S3) or local filesystem paths.

### Submit a job

```bash
curl -X POST "http://localhost:5600/replay/jobs" \
  -H "Content-Type: application/json" \
  -d '{"replay_url": "https://example.com/path/to/replay.dem.bz2"}'
```

To process a local replay file that is accessible to the parser host:

```bash
curl -X POST "http://localhost:5600/replay/jobs" \
  -H "Content-Type: application/json" \
  -d '{"file_path": "/absolute/path/to/replay.dem"}'
```

Exactly one of `replay_url` or `file_path` must be provided. The API responds with a `job_id` that can be polled for status:

```bash
curl "http://localhost:5600/replay/jobs/<job_id>"
```

When a job succeeds the response includes the parser JSON payload in the `result` field.

### Job dashboard

Visit `http://localhost:5600/` in a browser to see a live-updating HTML dashboard showing all recent jobs and their status. The page refreshes every 10 seconds.

### Tuning concurrency

Configure worker threads with `REPLAY_JOBS_CONCURRENT_WORKERS` (Spring property key `replay.jobs.concurrent-workers`). Default is 2 concurrent jobs.

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
- [docs/TESTING.md](docs/TESTING.md) - Comprehensive testing guide
- [scripts/README.md](scripts/README.md) - Scripts documentation