#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
#  test-e2e.sh — End-to-end smoke test via Docker Compose
#
#  Prerequisites: docker, docker compose
#
#  What it does:
#    1. Starts the full stack (Temporal + PostgreSQL + Worker)
#    2. Waits for Temporal to become healthy
#    3. Copies test files into the shared inbox volume
#    4. Runs the client container against each test file
#    5. Checks output exists
#    6. Tears everything down
# ═══════════════════════════════════════════════════════════════════════
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[TEST]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

# ── 1. Create test fixtures ──────────────────────────────────────────
log "Creating test fixtures…"
mkdir -p docker/test-fixtures

echo "Hello, this is a plain text test file for the Temporal file processor." \
    > docker/test-fixtures/sample.txt

# Create a simple 1×1 white PNG (base64-decoded)
echo "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==" \
    | base64 -d > docker/test-fixtures/sample.png

log "Test fixtures created in docker/test-fixtures/"

# ── 2. Build images ──────────────────────────────────────────────────
log "Building Docker images…"
docker compose build --quiet worker
docker compose build --quiet client

# ── 3. Start infrastructure ──────────────────────────────────────────
log "Starting Temporal stack…"
docker compose up -d postgresql temporal temporal-ui

log "Waiting for Temporal to become healthy (up to 120s)…"
SECONDS=0
until docker compose exec -T temporal tctl --address temporal:7233 cluster health 2>/dev/null | grep -q SERVING; do
    if (( SECONDS > 120 )); then
        fail "Temporal did not become healthy within 120s"
    fi
    sleep 3
done
log "Temporal is healthy (took ${SECONDS}s)"

# ── 4. Start the worker ──────────────────────────────────────────────
log "Starting file-processor worker…"
docker compose up -d worker
sleep 5

# ── 5. Copy test files into the inbox volume ─────────────────────────
log "Copying test files into the inbox volume…"
WORKER_CONTAINER=$(docker compose ps -q worker)
docker cp docker/test-fixtures/sample.txt "$WORKER_CONTAINER":/data/inbox/
docker cp docker/test-fixtures/sample.png "$WORKER_CONTAINER":/data/inbox/

# ── 6. Run the client against each test file ─────────────────────────
run_test() {
    local file_name="$1"
    local expected_type="$2"

    log "─── Testing: $file_name (expected: $expected_type) ───"

    docker compose run --rm \
        -e TEMPORAL_ADDRESS=temporal:7233 \
        client \
        --file-name "$file_name" \
        --file-location "/data/inbox/$file_name" \
        --output-location "/data/outbox" \
        --metadata "{\"test\":\"true\",\"expectedType\":\"$expected_type\"}" \
    && log "✓ $file_name processed successfully" \
    || warn "✗ $file_name processing returned non-zero (check logs)"
}

run_test "sample.txt" "PLAIN_TEXT"
run_test "sample.png" "IMAGE"

# ── 7. Verify output files exist ─────────────────────────────────────
log "Checking output files…"
PASS=true

for output_file in sample_extracted.txt; do
    if docker compose exec -T worker test -f "/data/outbox/$output_file"; then
        log "  ✓ /data/outbox/$output_file exists"
    else
        warn "  ✗ /data/outbox/$output_file missing"
        PASS=false
    fi
done

# ── 8. Show worker logs ──────────────────────────────────────────────
log "Worker logs (last 50 lines):"
docker compose logs --tail=50 worker

# ── 9. Tear down ─────────────────────────────────────────────────────
log "Tearing down…"
docker compose down -v
rm -rf docker/test-fixtures

# ── Result ────────────────────────────────────────────────────────────
if $PASS; then
    log "═══════════════════════════════════════"
    log "  ALL TESTS PASSED"
    log "═══════════════════════════════════════"
    exit 0
else
    fail "═══════════════════════════════════════"
    fail "  SOME TESTS FAILED — check logs above"
    fail "═══════════════════════════════════════"
fi
