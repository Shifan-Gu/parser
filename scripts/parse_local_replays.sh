#!/bin/bash

# Script to parse replay files from test-data folder using the local replay handler
# Usage: ./scripts/parse_local_replays.sh [test-data-directory]

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default test data directory
TEST_DATA_DIR="${1:-./test-data}"
PARSER_URL="http://localhost:5600"
OUTPUT_DIR="./parsed_output"

echo -e "${BLUE}=== Local Replay Parser ===${NC}"
echo -e "Test data directory: ${TEST_DATA_DIR}"
echo -e "Parser URL: ${PARSER_URL}"
echo ""

# Check if test data directory exists
if [ ! -d "$TEST_DATA_DIR" ]; then
    echo -e "${RED}Error: Test data directory not found: ${TEST_DATA_DIR}${NC}"
    exit 1
fi

# Check if parser service is running
if ! curl -s "${PARSER_URL}/healthz" > /dev/null 2>&1; then
    echo -e "${RED}Error: Parser service is not running at ${PARSER_URL}${NC}"
    echo -e "${YELLOW}Please start the parser service first with: mvn exec:java${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Parser service is running${NC}"
echo ""

# Create output directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

# Counter for statistics
total_files=0
successful=0
failed=0

# Find all .dem and .dem.bz2 files
echo -e "${BLUE}Searching for replay files...${NC}"
replay_files=$(find "$TEST_DATA_DIR" -type f \( -name "*.dem" -o -name "*.dem.bz2" \) | sort)

if [ -z "$replay_files" ]; then
    echo -e "${YELLOW}No replay files found in ${TEST_DATA_DIR}${NC}"
    exit 0
fi

echo -e "${GREEN}Found $(echo "$replay_files" | wc -l | tr -d ' ') replay file(s)${NC}"
echo ""

# Process each replay file
while IFS= read -r replay_file; do
    total_files=$((total_files + 1))
    filename=$(basename "$replay_file")
    
    echo -e "${BLUE}[$total_files] Processing: ${filename}${NC}"
    
    # Get absolute path
    abs_path=$(cd "$(dirname "$replay_file")" && pwd)/$(basename "$replay_file")
    
    # URL encode the file path
    encoded_path=$(echo "$abs_path" | sed 's/ /%20/g')
    
    # Parse the replay
    output_file="${OUTPUT_DIR}/${filename%.dem.bz2}.json"
    output_file="${output_file%.dem}.json"
    
    echo -e "  File path: ${abs_path}"
    echo -e "  Output: ${output_file}"
    
    # Make request to local replay handler
    if curl -s -f --max-time 300 "${PARSER_URL}/local?file_path=${encoded_path}" > "$output_file" 2>/dev/null; then
        file_size=$(du -h "$output_file" | cut -f1)
        echo -e "  ${GREEN}✓ Success${NC} (Output size: ${file_size})"
        successful=$((successful + 1))
    else
        exit_code=$?
        echo -e "  ${RED}✗ Failed${NC} (Exit code: ${exit_code})"
        failed=$((failed + 1))
        # Remove empty or failed output file
        rm -f "$output_file"
    fi
    
    echo ""
done <<< "$replay_files"

# Print summary
echo -e "${BLUE}=== Summary ===${NC}"
echo -e "Total files processed: ${total_files}"
echo -e "${GREEN}Successful: ${successful}${NC}"
echo -e "${RED}Failed: ${failed}${NC}"
echo ""

if [ $successful -gt 0 ]; then
    echo -e "${GREEN}Parsed data saved to: ${OUTPUT_DIR}${NC}"
fi

# Exit with error if any failed
if [ $failed -gt 0 ]; then
    exit 1
fi

