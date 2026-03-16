#!/usr/bin/env bash
# temporal-cli — convenience wrapper for the Temporal Workers CLI.
#
# Usage (inside container):
#   temporal-cli start Alice
#   temporal-cli start Alice --wait
#   temporal-cli status  --id hello-world-alice-1234
#   temporal-cli result  --id hello-world-alice-1234
#   temporal-cli describe --id hello-world-alice-1234 --history
#   temporal-cli cancel  --id hello-world-alice-1234
#   temporal-cli terminate --id hello-world-alice-1234 --force
#   temporal-cli list
#   temporal-cli list --status RUNNING
#   temporal-cli list --type HelloWorldWorkflow
#
# Usage (outside container):
#   docker compose exec workers temporal-cli start Alice --wait

exec java ${JAVA_OPTS:-} \
    -cp /app/app.jar \
    com.temporal.workers.cli.TemporalCli \
    "$@"
