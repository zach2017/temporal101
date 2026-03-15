#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────
#  Entrypoint: wait for Temporal server, then start
# ─────────────────────────────────────────────────

TEMPORAL_HOST="${TEMPORAL_HOST:-temporal}"
TEMPORAL_PORT="${TEMPORAL_PORT:-7233}"
MAX_RETRIES="${TEMPORAL_MAX_RETRIES:-30}"
RETRY_INTERVAL="${TEMPORAL_RETRY_INTERVAL:-2}"

echo "══════════════════════════════════════════════"
echo "  Temporal Worker Runner"
echo "══════════════════════════════════════════════"
echo "  Server  : ${TEMPORAL_HOST}:${TEMPORAL_PORT}"
echo "  Namespace: ${TEMPORAL_NAMESPACE:-default}"
echo "  Log Level: ${WORKER_LOG_LEVEL:-INFO}"
echo "══════════════════════════════════════════════"

# ── Wait for Temporal server ─────────────────────
echo ""
echo "⏳ Waiting for Temporal server at ${TEMPORAL_HOST}:${TEMPORAL_PORT} …"

attempt=0
until nc -z "${TEMPORAL_HOST}" "${TEMPORAL_PORT}" 2>/dev/null; do
    attempt=$((attempt + 1))
    if [ "${attempt}" -ge "${MAX_RETRIES}" ]; then
        echo "❌ Temporal server not reachable after ${MAX_RETRIES} attempts. Exiting."
        exit 1
    fi
    echo "   attempt ${attempt}/${MAX_RETRIES} — retrying in ${RETRY_INTERVAL}s …"
    sleep "${RETRY_INTERVAL}"
done

echo "✅ Temporal server is reachable."

# ── Run any setup scripts (namespace creation, etc.) ──
if [ -d /app/scripts/setup.d ]; then
    echo ""
    echo "🔧 Running setup scripts …"
    for script in /app/scripts/setup.d/*.sh; do
        [ -f "${script}" ] || continue
        echo "   → $(basename "${script}")"
        bash "${script}"
    done
fi

# ── Start workers ────────────────────────────────
echo ""
echo "🚀 Starting workers …"
exec python run_workers.py
