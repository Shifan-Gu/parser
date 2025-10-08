#!/bin/bash

set -e

echo "================================"
echo "S3/MinIO Test Script for Parser"
echo "================================"
echo ""

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# MinIO configuration
MINIO_HOST="localhost:9000"
MINIO_ACCESS_KEY="minioadmin"
MINIO_SECRET_KEY="minioadmin"
BUCKET_NAME="dota-replays"
TEST_FILE="test-data/7503212404.dem"

echo -e "${YELLOW}Step 1: Checking prerequisites...${NC}"

# Check if test file exists
if [ ! -f "$TEST_FILE" ]; then
    echo -e "${RED}Error: Test file not found at $TEST_FILE${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Test file found${NC}"

# Check if MinIO is running
echo -e "${YELLOW}Step 2: Checking MinIO availability...${NC}"
max_retries=30
retry_count=0
while ! curl -s http://$MINIO_HOST/minio/health/live > /dev/null; do
    retry_count=$((retry_count + 1))
    if [ $retry_count -ge $max_retries ]; then
        echo -e "${RED}Error: MinIO is not running. Please start it with: docker-compose -f docker-compose.dev.yml up minio${NC}"
        exit 1
    fi
    echo "Waiting for MinIO to be ready... (attempt $retry_count/$max_retries)"
    sleep 2
done
echo -e "${GREEN}✓ MinIO is running${NC}"

# Check if parser is running
echo -e "${YELLOW}Step 3: Checking Parser availability...${NC}"
max_retries=30
retry_count=0
while ! curl -s http://localhost:5600/healthz > /dev/null; do
    retry_count=$((retry_count + 1))
    if [ $retry_count -ge $max_retries ]; then
        echo -e "${RED}Error: Parser is not running. Please start it with: docker-compose -f docker-compose.dev.yml up parser${NC}"
        exit 1
    fi
    echo "Waiting for Parser to be ready... (attempt $retry_count/$max_retries)"
    sleep 2
done
echo -e "${GREEN}✓ Parser is running${NC}"

# Setup MinIO client (mc)
echo -e "${YELLOW}Step 4: Setting up MinIO client...${NC}"

# Check if mc (MinIO client) is installed
if ! command -v mc &> /dev/null; then
    echo "MinIO client (mc) not found. Installing..."
    
    # Detect OS and architecture
    OS=$(uname -s | tr '[:upper:]' '[:lower:]')
    ARCH=$(uname -m)
    
    if [ "$ARCH" = "x86_64" ]; then
        ARCH="amd64"
    elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
        ARCH="arm64"
    fi
    
    # Download mc binary
    MC_URL="https://dl.min.io/client/mc/release/${OS}-${ARCH}/mc"
    echo "Downloading from: $MC_URL"
    
    curl -o /tmp/mc "$MC_URL"
    chmod +x /tmp/mc
    MC_CMD="/tmp/mc"
else
    MC_CMD="mc"
fi

echo -e "${GREEN}✓ MinIO client ready${NC}"

# Configure MinIO alias
echo -e "${YELLOW}Step 5: Configuring MinIO connection...${NC}"
$MC_CMD alias set local http://$MINIO_HOST $MINIO_ACCESS_KEY $MINIO_SECRET_KEY
echo -e "${GREEN}✓ MinIO connection configured${NC}"

# Create bucket if it doesn't exist
echo -e "${YELLOW}Step 6: Creating bucket '$BUCKET_NAME'...${NC}"
if $MC_CMD ls local/$BUCKET_NAME &> /dev/null; then
    echo "Bucket already exists"
else
    $MC_CMD mb local/$BUCKET_NAME
    echo -e "${GREEN}✓ Bucket created${NC}"
fi

# Upload test file
echo -e "${YELLOW}Step 7: Uploading test replay to MinIO...${NC}"
OBJECT_KEY="replays/$(basename $TEST_FILE)"
$MC_CMD cp $TEST_FILE local/$BUCKET_NAME/$OBJECT_KEY
echo -e "${GREEN}✓ Test file uploaded to s3://$BUCKET_NAME/$OBJECT_KEY${NC}"

# Test S3 download
echo -e "${YELLOW}Step 8: Testing parser with S3 URL...${NC}"
S3_URL="s3://$BUCKET_NAME/$OBJECT_KEY"
echo "S3 URL: $S3_URL"

# Make request to parser
echo "Requesting parse via /blob endpoint..."
RESPONSE=$(curl -s -w "\n%{http_code}" "http://localhost:5600/blob?replay_url=$S3_URL")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

echo "HTTP Status Code: $HTTP_CODE"

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Parser successfully processed S3 replay!${NC}"
    echo ""
    echo "Response preview (first 500 chars):"
    echo "$BODY" | head -c 500
    echo ""
    echo "..."
    echo ""
    echo -e "${GREEN}SUCCESS: S3 integration is working!${NC}"
else
    echo -e "${RED}✗ Parser returned error status: $HTTP_CODE${NC}"
    echo "Response:"
    echo "$BODY"
    exit 1
fi

# Optional: List all files in bucket
echo ""
echo -e "${YELLOW}Files in MinIO bucket:${NC}"
$MC_CMD ls local/$BUCKET_NAME/$OBJECT_KEY

echo ""
echo "================================"
echo -e "${GREEN}All tests passed!${NC}"
echo "================================"
echo ""
echo "You can access MinIO console at: http://localhost:9001"
echo "Username: minioadmin"
echo "Password: minioadmin"
echo ""

