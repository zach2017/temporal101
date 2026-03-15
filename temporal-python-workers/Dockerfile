# ─────────────────────────────────────────────
#  Temporal Python Worker
# ─────────────────────────────────────────────
FROM python:3.12-slim AS base

# System deps needed at runtime
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        netcat-openbsd \
        curl \
    && rm -rf /var/lib/apt/lists/*

# ── Optional: install Temporal CLI for namespace setup ──
# Uncomment the lines below if you need the `temporal` CLI
# inside the container (e.g., for automatic namespace creation).
# ARG TEMPORAL_CLI_VERSION=1.1.2
# RUN curl -sSL -o /tmp/temporal.tar.gz \
#         "https://temporal.download/cli/archive/v${TEMPORAL_CLI_VERSION}?platform=linux&arch=amd64" && \
#     tar -xzf /tmp/temporal.tar.gz -C /usr/local/bin && \
#     rm /tmp/temporal.tar.gz

WORKDIR /app

# ── Python dependencies ──────────────────────
COPY pyproject.toml ./
RUN pip install --no-cache-dir . && \
    pip install --no-cache-dir pip-tools

# ── Application code ─────────────────────────
COPY config/       ./config/
COPY workers/      ./workers/
COPY scripts/      ./scripts/
COPY registry.py   ./
COPY run_workers.py ./
COPY start_hello.py ./

# Make scripts executable
RUN chmod +x scripts/entrypoint.sh scripts/setup.d/*.sh

# ── Runtime ──────────────────────────────────
ENV PYTHONUNBUFFERED=1
ENV TEMPORAL_HOST=temporal
ENV TEMPORAL_PORT=7233
ENV TEMPORAL_NAMESPACE=default
ENV WORKER_LOG_LEVEL=INFO

ENTRYPOINT ["scripts/entrypoint.sh"]
