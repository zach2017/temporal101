#!/usr/bin/env bash
# 01-register-namespace.sh
# Ensures the configured namespace exists in Temporal.
# Requires `tctl` or `temporal` CLI to be on $PATH inside the container.

set -euo pipefail

NAMESPACE="${TEMPORAL_NAMESPACE:-default}"

if [ "${NAMESPACE}" = "default" ]; then
    echo "   Using 'default' namespace — no registration needed."
    exit 0
fi

echo "   Registering namespace '${NAMESPACE}' (if it doesn't exist) …"

if command -v temporal &>/dev/null; then
    temporal operator namespace create "${NAMESPACE}" 2>/dev/null \
        && echo "   Namespace '${NAMESPACE}' created." \
        || echo "   Namespace '${NAMESPACE}' already exists."
elif command -v tctl &>/dev/null; then
    tctl --namespace "${NAMESPACE}" namespace register 2>/dev/null \
        && echo "   Namespace '${NAMESPACE}' created." \
        || echo "   Namespace '${NAMESPACE}' already exists."
else
    echo "   ⚠  Neither 'temporal' nor 'tctl' CLI found — skipping namespace registration."
    echo "   Make sure the namespace '${NAMESPACE}' exists on the server."
fi
