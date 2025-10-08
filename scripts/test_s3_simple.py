#!/usr/bin/env python3
"""
Simple S3/MinIO test script for the parser.
This script uses boto3 (AWS SDK for Python) to upload test data and test the S3 feature.
"""

import sys
import time
import urllib.request
import urllib.parse
import os

try:
    import boto3
    from botocore.client import Config
except ImportError:
    print("Error: boto3 is required. Install it with: pip install boto3")
    sys.exit(1)

# Configuration
MINIO_ENDPOINT = "http://localhost:9000"
MINIO_ACCESS_KEY = "minioadmin"
MINIO_SECRET_KEY = "minioadmin"
BUCKET_NAME = "dota-replays"
TEST_FILE = "test-data/7503212404.dem"
PARSER_URL = "http://localhost:5600"

def print_status(message, status="info"):
    """Print colored status messages."""
    colors = {
        "info": "\033[1;33m",  # Yellow
        "success": "\033[0;32m",  # Green
        "error": "\033[0;31m",  # Red
    }
    reset = "\033[0m"
    color = colors.get(status, "")
    print(f"{color}{message}{reset}")

def wait_for_service(url, name, max_retries=30):
    """Wait for a service to be available."""
    print_status(f"Waiting for {name} to be ready...", "info")
    for i in range(max_retries):
        try:
            urllib.request.urlopen(url, timeout=2)
            print_status(f"✓ {name} is ready", "success")
            return True
        except:
            if i == max_retries - 1:
                print_status(f"✗ {name} is not responding", "error")
                return False
            time.sleep(2)
    return False

def main():
    print("=" * 50)
    print("S3/MinIO Test Script for Parser (Python)")
    print("=" * 50)
    print()

    # Check if test file exists
    print_status("Step 1: Checking test file...", "info")
    if not os.path.exists(TEST_FILE):
        print_status(f"✗ Test file not found: {TEST_FILE}", "error")
        sys.exit(1)
    print_status(f"✓ Test file found: {TEST_FILE}", "success")

    # Wait for MinIO
    print_status("Step 2: Checking MinIO availability...", "info")
    if not wait_for_service(f"{MINIO_ENDPOINT}/minio/health/live", "MinIO"):
        print_status("Please start MinIO: docker-compose -f docker-compose.dev.yml up minio", "error")
        sys.exit(1)

    # Wait for Parser
    print_status("Step 3: Checking Parser availability...", "info")
    if not wait_for_service(f"{PARSER_URL}/healthz", "Parser"):
        print_status("Please start Parser: docker-compose -f docker-compose.dev.yml up parser", "error")
        sys.exit(1)

    # Setup S3 client
    print_status("Step 4: Setting up S3 client...", "info")
    s3_client = boto3.client(
        's3',
        endpoint_url=MINIO_ENDPOINT,
        aws_access_key_id=MINIO_ACCESS_KEY,
        aws_secret_access_key=MINIO_SECRET_KEY,
        config=Config(signature_version='s3v4'),
        region_name='us-east-1'
    )
    print_status("✓ S3 client configured", "success")

    # Create bucket
    print_status(f"Step 5: Creating bucket '{BUCKET_NAME}'...", "info")
    try:
        s3_client.head_bucket(Bucket=BUCKET_NAME)
        print_status("Bucket already exists", "info")
    except:
        s3_client.create_bucket(Bucket=BUCKET_NAME)
        print_status("✓ Bucket created", "success")

    # Upload file
    print_status("Step 6: Uploading test replay to MinIO...", "info")
    object_key = f"replays/{os.path.basename(TEST_FILE)}"
    
    file_size = os.path.getsize(TEST_FILE)
    print(f"Uploading {TEST_FILE} ({file_size} bytes)...")
    
    with open(TEST_FILE, 'rb') as f:
        s3_client.upload_fileobj(f, BUCKET_NAME, object_key)
    
    s3_url = f"s3://{BUCKET_NAME}/{object_key}"
    print_status(f"✓ File uploaded to {s3_url}", "success")

    # Test parser
    print_status("Step 7: Testing parser with S3 URL...", "info")
    print(f"S3 URL: {s3_url}")
    
    encoded_url = urllib.parse.quote(s3_url)
    request_url = f"{PARSER_URL}/blob?replay_url={encoded_url}"
    print(f"Requesting: {request_url}")
    
    try:
        response = urllib.request.urlopen(request_url, timeout=300)
        status_code = response.getcode()
        body = response.read().decode('utf-8')
        
        print(f"HTTP Status Code: {status_code}")
        
        if status_code == 200:
            print_status("✓ Parser successfully processed S3 replay!", "success")
            print()
            print("Response preview (first 500 chars):")
            print(body[:500])
            if len(body) > 500:
                print("...")
            print()
            print_status("SUCCESS: S3 integration is working!", "success")
        else:
            print_status(f"✗ Parser returned status: {status_code}", "error")
            print("Response:", body)
            sys.exit(1)
    except urllib.error.HTTPError as e:
        print_status(f"✗ HTTP Error: {e.code}", "error")
        print(e.read().decode('utf-8'))
        sys.exit(1)
    except Exception as e:
        print_status(f"✗ Error: {e}", "error")
        sys.exit(1)

    # Show bucket contents
    print()
    print_status("Files in MinIO bucket:", "info")
    objects = s3_client.list_objects_v2(Bucket=BUCKET_NAME)
    if 'Contents' in objects:
        for obj in objects['Contents']:
            print(f"  - {obj['Key']} ({obj['Size']} bytes)")

    print()
    print("=" * 50)
    print_status("All tests passed!", "success")
    print("=" * 50)
    print()
    print("You can access MinIO console at: http://localhost:9001")
    print("Username: minioadmin")
    print("Password: minioadmin")
    print()

if __name__ == "__main__":
    main()

