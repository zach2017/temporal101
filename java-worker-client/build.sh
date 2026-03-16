#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────
#  build.sh — Build the fat JAR on the host
# ─────────────────────────────────────────────
#
#  Usage:
#    ./build.sh              # clean + package
#    ./build.sh --skip-tests # skip tests
#
#  Requires: Java 21+
#  Maven: auto-downloaded via ./mvnw if needed
# ─────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

# ── Check Java version ──────────────────────
if ! command -v java &>/dev/null; then
    echo "❌ Java not found. Install JDK 21+ and try again."
    echo "   https://adoptium.net/temurin/releases/?version=21"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}' | cut -d. -f1)
if [ "${JAVA_VER}" -lt 21 ] 2>/dev/null; then
    echo "❌ Java 21+ required. Found: Java ${JAVA_VER}."
    echo "   https://adoptium.net/temurin/releases/?version=21"
    exit 1
fi
echo "✅ Java ${JAVA_VER} detected."

# ── Use Maven wrapper (auto-downloads 3.9.9) ──
MVN="${SCRIPT_DIR}/mvnw"
if [ ! -x "${MVN}" ]; then
    chmod +x "${MVN}"
fi
echo "✅ Using: ${MVN}"

# ── Parse args ───────────────────────────────
EXTRA_ARGS=""
if [[ "${1:-}" == "--skip-tests" ]]; then
    EXTRA_ARGS="-DskipTests"
fi

# ── Build ────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════"
echo "  Building temporal-workers-java"
echo "══════════════════════════════════════════════"
echo ""

${MVN} clean package -B ${EXTRA_ARGS}

JAR_PATH="target/temporal-workers-java-0.1.0.jar"
if [ -f "${JAR_PATH}" ]; then
    echo ""
    echo "══════════════════════════════════════════════"
    echo "  ✅ Build successful!"
    echo "══════════════════════════════════════════════"
    echo ""
    echo "  Fat JAR: ${JAR_PATH}"
    echo ""
    echo "  Run workers:"
    echo "    ./run.sh workers"
    echo ""
    echo "  Run CLI:"
    echo "    ./run.sh cli start Alice --wait"
    echo "    ./run.sh cli list"
    echo "    ./run.sh cli status --id <WORKFLOW_ID>"
    echo ""
fi
