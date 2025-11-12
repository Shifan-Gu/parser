#!/bin/bash

# Script to parse a single replay file using the local replay handler
# Usage: ./scripts/parse_single_replay.sh <replay-file-path> [output-file]

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PARSER_URL="http://localhost:5600"

# Check arguments
if [ $# -eq 0 ]; then
    echo -e "${RED}Error: No replay file specified${NC}"
    echo -e "Usage: $0 <replay-file-path> [output-file]"
    echo -e ""
    echo -e "Example:"
    echo -e "  $0 test-data/8461476910_1623987332.dem.bz2"
    echo -e "  $0 test-data/8461476910_1623987332.dem.bz2 output.json"
    exit 1
fi

REPLAY_FILE="$1"
OUTPUT_FILE="${2:-parsed_output.json}"

echo -e "${BLUE}=== Single Replay Parser ===${NC}"
echo -e "Replay file: ${REPLAY_FILE}"
echo -e "Output file: ${OUTPUT_FILE}"
echo -e "Parser URL: ${PARSER_URL}"
echo ""

# Check if replay file exists
if [ ! -f "$REPLAY_FILE" ]; then
    echo -e "${RED}Error: Replay file not found: ${REPLAY_FILE}${NC}"
    exit 1
fi

# Check if parser service is running
echo -e "Checking parser service..."
if ! curl -s "${PARSER_URL}/healthz" > /dev/null 2>&1; then
    echo -e "${RED}Error: Parser service is not running at ${PARSER_URL}${NC}"
    echo -e "${YELLOW}Please start the parser service first with: mvn exec:java${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Parser service is running${NC}"
echo ""

# Get absolute path
ABS_PATH=$(cd "$(dirname "$REPLAY_FILE")" && pwd)/$(basename "$REPLAY_FILE")
echo -e "Absolute path: ${ABS_PATH}"

# URL encode the file path
ENCODED_PATH=$(echo "$ABS_PATH" | sed 's/ /%20/g')

# Parse the replay
echo -e "${BLUE}Parsing replay...${NC}"
START_TIME=$(date +%s)

if curl -s -f --max-time 300 "${PARSER_URL}/local?file_path=${ENCODED_PATH}" > "$OUTPUT_FILE" 2>/dev/null; then
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    FILE_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)
    
    echo -e "${GREEN}✓ Success!${NC}"
    echo -e "  Duration: ${DURATION}s"
    echo -e "  Output size: ${FILE_SIZE}"
    echo -e "  Output file: ${OUTPUT_FILE}"
    
    # Show a preview of the JSON if jq is available
    if command -v jq &> /dev/null; then
        echo -e ""
        echo -e "${BLUE}JSON Preview:${NC}"
        jq '.' "$OUTPUT_FILE" | head -20
        echo -e "..."
    fi
else
    EXIT_CODE=$?
    echo -e "${RED}✗ Failed to parse replay${NC} (Exit code: ${EXIT_CODE})"
    rm -f "$OUTPUT_FILE"
    exit 1
fi

