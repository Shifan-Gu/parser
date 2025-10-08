#!/bin/bash

# Parser Test Script
# Tests the parser with a sample replay file

set -e

echo "================================"
echo "Parser Basic Test"
echo "================================"
echo ""

PARSER_URL="http://localhost:5600"
SAMPLE_REPLAY_URL="http://replay271.valve.net/570/8500517109_1115614565.dem.bz2"

# Check if parser is running
echo "Checking parser availability..."
if ! curl -sf "$PARSER_URL/healthz" > /dev/null; then
    echo "Error: Parser is not running at $PARSER_URL"
    echo "Start it with: docker-compose -f docker-compose.dev.yml up parser"
    exit 1
fi
echo "✓ Parser is running"
echo ""

# Test with HTTP replay URL
echo "Testing parser with HTTP replay URL..."
echo "URL: $SAMPLE_REPLAY_URL"
echo ""

RESPONSE=$(curl -s -w "\n%{http_code}" "$PARSER_URL/blob?replay_url=$SAMPLE_REPLAY_URL")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

echo "HTTP Status: $HTTP_CODE"

if [ "$HTTP_CODE" = "200" ]; then
    echo "✓ Parser test PASSED"
    echo ""
    echo "Response preview (first 500 chars):"
    echo "$BODY" | head -c 500
    echo ""
    echo "..."
    echo ""
    echo "Full response length: $(echo "$BODY" | wc -c) bytes"
else
    echo "✗ Parser test FAILED"
    echo "Response:"
    echo "$BODY"
    exit 1
fi

echo ""
echo "================================"
echo "Test completed successfully!"
echo "================================"
