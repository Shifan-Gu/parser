# parser
Replay parse server generating logs from replay files

## Features

* Parse Dota 2 replay files (.dem and .dem.bz2)
* HTTP/HTTPS download support
* **S3 integration** - Download replays directly from S3 buckets (AWS S3, MinIO, etc.)
* Database storage with PostgreSQL
* JSON output with detailed match statistics

## Quickstart

* Run the Java project (it'll start a webserver on port 5600)
* Or build manually with `mvn install`
* Run manually with `java -jar target/stats-0.1.0.jar`
* POST a .dem replay file to the server (example: scripts/test.sh)
* The parser returns line-delimited JSON in the HTTP response

## S3 Support

The parser can download replay files directly from S3 buckets. See [README_S3.md](README_S3.md) for detailed setup and usage instructions.

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

# Test S3 integration
bash scripts/test_s3.sh
# or
python3 scripts/test_s3_simple.py
```

### Manual Build

```bash
mvn clean install
java -jar target/stats-0.1.0.jar
```

## Documentation

- [README_S3.md](README_S3.md) - S3 setup and configuration guide
- [README_DATABASE.md](README_DATABASE.md) - Database setup and usage