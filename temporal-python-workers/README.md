# Temporal Python Workers

An extensible Temporal worker framework in Python. Ships with a **Hello World** long-running worker and is designed so new workers can be dropped in with minimal boilerplate.

## Architecture

```
temporal-workers/
├── config/                # Centralized settings (env-driven)
│   ├── __init__.py
│   └── settings.py
├── workers/               # ← Each sub-package = one worker
│   ├── __init__.py
│   └── hello_world/       # Hello World (long-running)
│       ├── __init__.py    #   exposes `register` dict
│       ├── activities.py  #   heartbeating long-running activity
│       └── workflows.py   #   workflow that orchestrates the activity
├── registry.py            # Auto-discovers workers from a module list
├── run_workers.py         # Entrypoint — connects & starts all workers
├── start_hello.py         # CLI to trigger the Hello World workflow
├── scripts/
│   ├── entrypoint.sh      # Docker entrypoint (wait + setup + run)
│   └── setup.d/           # Drop-in setup scripts (namespace, etc.)
├── Dockerfile
├── docker-compose.yml     # Full local stack: Temporal + UI + workers
└── pyproject.toml
```

## Quick Start

### With Docker (recommended)

```bash
# Start everything: Temporal server, UI, and workers
docker compose up --build

# In another terminal, trigger the Hello World workflow
docker compose exec workers python start_hello.py "Alice"
```

Open the Temporal UI at **http://localhost:8080** to watch the workflow execute its 20 long-running steps.

### Without Docker

```bash
# 1. Install dependencies
pip install -e .

# 2. Make sure a Temporal server is running on localhost:7233

# 3. Start the workers
python run_workers.py

# 4. In another terminal, trigger the workflow
python start_hello.py "Bob"
```

## How the Hello World Worker Works

The Hello World worker demonstrates a **long-running** Temporal activity:

1. **`HelloWorldWorkflow`** receives a name and delegates to the activity.
2. **`say_hello_long_running`** runs 20 steps (configurable), each taking 3 seconds.
3. Every step **heartbeats** its progress back to Temporal.
4. If the worker crashes, Temporal reschedules the activity on another worker, and it **resumes from the last heartbeat** — no work is repeated.
5. Timeouts: 30-minute start-to-close, 30-second heartbeat. Five retries with exponential backoff.

## Adding a New Worker

1. Create `workers/my_worker/`:
   ```
   workers/my_worker/
   ├── __init__.py
   ├── activities.py
   └── workflows.py
   ```

2. Define your workflows and activities as usual with `@workflow.defn` and `@activity.defn`.

3. In `workers/my_worker/__init__.py`, expose a **`register`** dict:
   ```python
   from workers.my_worker.activities import do_stuff
   from workers.my_worker.workflows import MyWorkflow

   register = {
       "task_queue": "my-worker-queue",
       "workflows": [MyWorkflow],
       "activities": [do_stuff],
   }
   ```

4. Add the module to `WORKER_MODULES` in `registry.py`:
   ```python
   WORKER_MODULES = [
       "workers.hello_world",
       "workers.my_worker",   # ← new
   ]
   ```

5. Rebuild and restart:
   ```bash
   docker compose up --build
   ```

## Configuration

All settings are driven by environment variables:

| Variable | Default | Description |
|---|---|---|
| `TEMPORAL_HOST` | `localhost` | Temporal gRPC host |
| `TEMPORAL_PORT` | `7233` | Temporal gRPC port |
| `TEMPORAL_NAMESPACE` | `default` | Temporal namespace |
| `WORKER_LOG_LEVEL` | `INFO` | Python log level |
| `WORKER_MAX_CONCURRENT_ACTIVITIES` | `10` | Max parallel activities per worker |
| `WORKER_MAX_CONCURRENT_WORKFLOWS` | `10` | Max parallel workflow tasks per worker |

## Setup Scripts

Drop any `.sh` file into `scripts/setup.d/` and it will run (in alphabetical order) after the Temporal server is reachable but before workers start. Use this for namespace creation, schema migrations, seed data, etc.
