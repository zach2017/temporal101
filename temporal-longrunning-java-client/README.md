# Temporal Long-Running Workflow — Java CLI Client

Java client for the Python `LongRunningWorkflow` worker.

## Requirements
- Java 11+
- Maven 3.6+
- Python long-running worker running (`python worker.py`)
- Temporal server on `localhost:7233`

## Build

```bash
mvn package -q
# → target/temporal-longrunning-client.jar
```

## Commands

### Start (sync — blocks until done)
```bash
java -jar target/temporal-longrunning-client.jar start \
  --job-id my-job --steps 5 --task-queue long-running-queue
```

### Start async (returns immediately)
```bash
java -jar target/temporal-longrunning-client.jar start-async \
  --job-id my-job --steps 10
# → Workflow ID: long-running-my-job
# → Check status: java -jar ... status --workflow-id long-running-my-job
```

### Check status (non-blocking)
```bash
java -jar target/temporal-longrunning-client.jar status \
  --workflow-id long-running-my-job
# → 🔄 RUNNING  |  Running for: 14s
```

### Fetch result (blocks if still running)
```bash
java -jar target/temporal-longrunning-client.jar result \
  --workflow-id long-running-my-job
# → Result: Job 'my-job' finished — 10 steps completed successfully.
```

## Env vars

| Variable        | Default     | Description           |
|-----------------|-------------|-----------------------|
| `TEMPORAL_HOST` | `localhost` | Temporal server host  |
| `TEMPORAL_PORT` | `7233`      | Temporal server port  |

```bash
export TEMPORAL_HOST=my-temporal.internal
export TEMPORAL_PORT=7233
java -jar target/temporal-longrunning-client.jar start-async --steps 20
```

## Typical async workflow

```
Terminal 1 (Python):  python worker.py
Terminal 2 (Java):    java -jar ... start-async --job-id batch-001 --steps 15
                      → long-running-batch-001
Terminal 3 (Java):    java -jar ... status --workflow-id long-running-batch-001
                      → 🔄 RUNNING  Running for: 8s
Terminal 3 (Java):    java -jar ... result --workflow-id long-running-batch-001
                      → Result: Job 'batch-001' finished — 15 steps completed.
```
