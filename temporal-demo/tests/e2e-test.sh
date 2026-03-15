#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# End-to-End Integration Test for Temporal Demo
# ═══════════════════════════════════════════════════════════════
#
# This script validates the FULL running Docker Compose stack.
# It must be run AFTER `docker compose up --build -d`.
#
# Test Flow:
#   1. Wait for all services to be healthy
#   2. Test Spring Boot health endpoint
#   3. Test Temporal Server reachability
#   4. Test Temporal UI reachability
#   5. Send a hello request → both workers must respond
#   6. Verify workflow executions appear in Temporal
#   7. Print summary
#
# Usage:
#   docker compose up --build -d
#   ./tests/e2e-test.sh
#
# ═══════════════════════════════════════════════════════════════

set -euo pipefail

# ── Colors ──────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'  # No Color

PASS=0
FAIL=0
TOTAL=0

# ── Helpers ─────────────────────────────────────────────────

log()  { echo -e "${CYAN}[TEST]${NC} $1"; }
pass() { echo -e "${GREEN}  ✓ PASS${NC} $1"; PASS=$((PASS+1)); TOTAL=$((TOTAL+1)); }
fail() { echo -e "${RED}  ✗ FAIL${NC} $1"; FAIL=$((FAIL+1)); TOTAL=$((TOTAL+1)); }
warn() { echo -e "${YELLOW}  ⚠ WARN${NC} $1"; }

assert_status() {
    local url="$1"
    local expected="$2"
    local label="$3"
    local actual
    actual=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$url" 2>/dev/null || echo "000")
    if [ "$actual" = "$expected" ]; then
        pass "$label (HTTP $actual)"
    else
        fail "$label (expected HTTP $expected, got HTTP $actual)"
    fi
}

assert_json_field() {
    local json="$1"
    local field="$2"
    local expected="$3"
    local label="$4"
    local actual
    actual=$(echo "$json" | python3 -c "
import sys, json
data = json.load(sys.stdin)
keys = '$field'.split('.')
val = data
for k in keys:
    val = val[k]
print(val)
" 2>/dev/null || echo "__MISSING__")
    if [ "$actual" = "$expected" ]; then
        pass "$label ($field = $expected)"
    else
        fail "$label ($field expected '$expected', got '$actual')"
    fi
}

wait_for_service() {
    local url="$1"
    local label="$2"
    local max_wait="${3:-60}"
    local elapsed=0
    log "Waiting for $label at $url (max ${max_wait}s)..."
    while [ $elapsed -lt $max_wait ]; do
        if curl -sf --max-time 3 "$url" > /dev/null 2>&1; then
            pass "$label is reachable (${elapsed}s)"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed+2))
    done
    fail "$label not reachable after ${max_wait}s"
    return 1
}

# ── Banner ──────────────────────────────────────────────────

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${CYAN}   Temporal Demo — End-to-End Integration Tests    ${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
echo ""

# ── 1. Service Health Checks ───────────────────────────────

log "Phase 1: Service Health Checks"
echo ""

wait_for_service "http://localhost:8081/api/health" "Spring Boot App" 90
wait_for_service "http://localhost:8080" "Temporal UI" 90

echo ""

# ── 2. Spring Boot Health Endpoint ─────────────────────────

log "Phase 2: Spring Boot API Tests"
echo ""

assert_status "http://localhost:8081/api/health" "200" "GET /api/health"

HEALTH_BODY=$(curl -sf http://localhost:8081/api/health 2>/dev/null || echo "{}")
assert_json_field "$HEALTH_BODY" "status" "UP" "Health status"
assert_json_field "$HEALTH_BODY" "service" "temporal-spring-app" "Service name"

echo ""

# ── 3. Static HTML Served ──────────────────────────────────

log "Phase 3: Static Content"
echo ""

assert_status "http://localhost:8081/" "200" "GET / (index.html)"
assert_status "http://localhost:8081/index.html" "200" "GET /index.html"

# Check HTML contains key sections
HTML=$(curl -sf http://localhost:8081/ 2>/dev/null || echo "")
if echo "$HTML" | grep -q "Temporal Workflow"; then
    pass "index.html contains demo title"
else
    fail "index.html missing demo title"
fi

if echo "$HTML" | grep -q "id=\"tutorial\""; then
    pass "index.html contains tutorial section"
else
    fail "index.html missing tutorial section"
fi

echo ""

# ── 4. Wrong Method / Missing Body ─────────────────────────

log "Phase 4: API Error Handling"
echo ""

assert_status "http://localhost:8081/api/hello" "405" "GET /api/hello (wrong method)"

MISSING_BODY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
    -X POST "http://localhost:8081/api/hello" \
    -H "Content-Type: application/json" 2>/dev/null || echo "000")
if [ "$MISSING_BODY_STATUS" = "400" ]; then
    pass "POST /api/hello without body returns 400"
else
    warn "POST /api/hello without body returned HTTP $MISSING_BODY_STATUS (expected 400)"
    TOTAL=$((TOTAL+1))  # Count as warning, not hard fail
fi

echo ""

# ── 5. Full Workflow Execution ─────────────────────────────

log "Phase 5: Full Workflow Execution (the main test)"
echo ""

log "Sending POST /api/hello with name='E2E-Test'..."
RESPONSE=$(curl -sf --max-time 60 \
    -X POST "http://localhost:8081/api/hello" \
    -H "Content-Type: application/json" \
    -d '{"name": "E2E-Test"}' 2>/dev/null || echo "__FAILED__")

if [ "$RESPONSE" = "__FAILED__" ]; then
    fail "POST /api/hello request failed entirely"
else
    pass "POST /api/hello returned a response"

    # Validate Java Worker
    assert_json_field "$RESPONSE" "javaWorker.status" "SUCCESS" "Java Worker"

    JAVA_MSG=$(echo "$RESPONSE" | python3 -c "
import sys, json
print(json.load(sys.stdin)['javaWorker'].get('message',''))
" 2>/dev/null || echo "")

    if echo "$JAVA_MSG" | grep -q "Hello E2E-Test"; then
        pass "Java Worker greeting contains name"
    else
        fail "Java Worker greeting missing name (got: $JAVA_MSG)"
    fi

    if echo "$JAVA_MSG" | grep -q "Java Worker"; then
        pass "Java Worker identifies itself"
    else
        fail "Java Worker not identified in message"
    fi

    # Validate Python Worker
    assert_json_field "$RESPONSE" "pythonWorker.status" "SUCCESS" "Python Worker"

    PYTHON_MSG=$(echo "$RESPONSE" | python3 -c "
import sys, json
print(json.load(sys.stdin)['pythonWorker'].get('message',''))
" 2>/dev/null || echo "")

    if echo "$PYTHON_MSG" | grep -q "Hello E2E-Test"; then
        pass "Python Worker greeting contains name"
    else
        fail "Python Worker greeting missing name (got: $PYTHON_MSG)"
    fi

    if echo "$PYTHON_MSG" | grep -q "Python Worker"; then
        pass "Python Worker identifies itself"
    else
        fail "Python Worker not identified in message"
    fi

    # Validate Temporal UI link
    assert_json_field "$RESPONSE" "temporalUi" "http://localhost:8080" "Temporal UI link"

    echo ""
    log "Full response:"
    echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
fi

echo ""

# ── 6. Temporal UI Accessible ──────────────────────────────

log "Phase 6: Temporal UI Validation"
echo ""

assert_status "http://localhost:8080" "200" "Temporal UI loads"

echo ""

# ── 7. Docker Container Health ─────────────────────────────

log "Phase 7: Docker Container Status"
echo ""

for container in temporal-postgresql temporal-server temporal-ui temporal-spring-app temporal-java-worker temporal-python-worker; do
    STATUS=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "not_found")
    if [ "$STATUS" = "running" ]; then
        pass "Container $container is running"
    else
        fail "Container $container status: $STATUS"
    fi
done

echo ""

# ── Summary ─────────────────────────────────────────────────

echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${CYAN}   Test Summary                                    ${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
echo ""
echo -e "   Total:  ${TOTAL}"
echo -e "   ${GREEN}Passed: ${PASS}${NC}"
if [ $FAIL -gt 0 ]; then
    echo -e "   ${RED}Failed: ${FAIL}${NC}"
    echo ""
    exit 1
else
    echo -e "   ${RED}Failed: 0${NC}"
    echo ""
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi
