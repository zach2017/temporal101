#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────
#  run.sh — Run workers or CLI from the host
# ─────────────────────────────────────────────
#
#  Usage:
#    ./run.sh workers                           # Start all workers
#    ./run.sh cli start Alice --wait            # Start workflow (wait)
#    ./run.sh cli start Alice                   # Start workflow (async)
#    ./run.sh cli status --id <ID>              # Check status
#    ./run.sh cli result --id <ID>              # Get result
#    ./run.sh cli describe --id <ID> --history  # Full details
#    ./run.sh cli cancel --id <ID>              # Cancel
#    ./run.sh cli terminate --id <ID> --force   # Terminate
#    ./run.sh cli list                          # List workflows
#    ./run.sh cli list --status RUNNING         # Filter by status
#    ./run.sh cli --help                        # CLI help
#
#  Environment variables:
#    TEMPORAL_HOST      (default: localhost)
#    TEMPORAL_PORT      (default: 7233)
#    TEMPORAL_NAMESPACE (default: default)
#    WORKER_LOG_LEVEL   (default: INFO)
#    JAVA_OPTS          (default: -Djava.net.preferIPv4Stack=true)
# ─────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${SCRIPT_DIR}/target/temporal-workers-java-0.1.0.jar"

JAVA_OPTS="${JAVA_OPTS:--Djava.net.preferIPv4Stack=true}"

# ── Verify JAR exists ────────────────────────
if [ ! -f "${JAR_PATH}" ]; then
    echo "❌ Fat JAR not found at: ${JAR_PATH}"
    echo "   Run ./build.sh first."
    exit 1
fi

# ── Parse command ────────────────────────────
COMMAND="${1:-}"

if [ -z "${COMMAND}" ]; then
    echo "Usage:"
    echo "  ./run.sh workers                   Start all Temporal workers"
    echo "  ./run.sh cli <command> [options]    Run the CLI client"
    echo ""
    echo "Examples:"
    echo "  ./run.sh workers"
    echo "  ./run.sh cli start Alice --wait"
    echo "  ./run.sh cli list --status RUNNING"
    echo "  ./run.sh cli --help"
    exit 0
fi

shift

case "${COMMAND}" in
    workers|worker)
        echo "══════════════════════════════════════════════"
        echo "  Starting Temporal Workers"
        echo "══════════════════════════════════════════════"
        echo "  Server  : ${TEMPORAL_HOST:-localhost}:${TEMPORAL_PORT:-7233}"
        echo "  Namespace: ${TEMPORAL_NAMESPACE:-default}"
        echo "══════════════════════════════════════════════"
        echo ""
        # shellcheck disable=SC2086
        exec java ${JAVA_OPTS} -jar "${JAR_PATH}" "$@"
        ;;
    cli)
        # shellcheck disable=SC2086
        exec java ${JAVA_OPTS} \
            -cp "${JAR_PATH}" \
            com.temporal.workers.cli.TemporalCli \
            "$@"
        ;;
    *)
        echo "❌ Unknown command: ${COMMAND}"
        echo ""
        echo "Valid commands: workers, cli"
        exit 1
        ;;
esac
