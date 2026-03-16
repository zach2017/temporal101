# Temporal Builder

A drag-and-drop UI for designing Temporal.io worker and client interfaces, backed by a FastAPI code generator that produces production-ready Python Temporal code from your YAML config.

## Architecture

```
┌─────────────────────────┐      ┌─────────────────────────┐
│   Frontend (nginx:3000) │─────▶│  Backend (FastAPI:8000)  │
│                         │ /api │                          │
│  • Drag & drop UI       │      │  • YAML parser           │
│  • Type builder         │      │  • Code generator        │
│  • YAML preview         │      │  • File storage          │
│  • Results viewer       │      │  • ZIP downloads         │
└─────────────────────────┘      └──────────┬──────────────┘
                                            │
                                  ┌─────────▼──────────┐
                                  │  ./generated/       │
                                  │                     │
                                  │  project_id/        │
                                  │  ├── config.yaml    │
                                  │  ├── types.py       │
                                  │  ├── activities.py  │
                                  │  ├── workflows.py   │
                                  │  ├── worker_*.py    │
                                  │  ├── client_*.py    │
                                  │  ├── Dockerfile     │
                                  │  └── docker-compose │
                                  └────────────────────┘
```

## Quick Start

```bash
docker compose up --build
```

Then open **http://localhost:3000** in your browser.

## Usage

### 1. Design Your Interface

Use the tabbed builder to define:

- **Types** — Drag primitive types (string, int, bool, etc.) into struct fields. Create enums and aliases.
- **Activities** — Define sync (blocking) or async (heartbeating) activities with I/O types and timeouts.
- **Workflows** — Build sync, async, or cron workflows with step sequences that reference your activities.
- **Pipelines** — Orchestrate multi-workflow processes with sequential, parallel, or fan-out stages.
- **Workers** — Assign activities and workflows to worker processes, tune concurrency and resources.
- **Clients** — Configure typed clients with connection settings and workflow allow-lists.

### 2. Preview YAML

Click **Preview YAML** to inspect the generated configuration before submitting.

### 3. Generate Code

Click **Generate** to send the YAML to the API. The backend produces:

| File | Contents |
|---|---|
| `types.py` | Python dataclasses and enums for all defined types |
| `activities.py` | `@activity.defn` stubs with retry policies and heartbeat wiring |
| `workflows.py` | `@workflow.defn` classes with steps, signal/query/update handlers |
| `worker_*.py` | Runnable worker entry points with concurrency config |
| `client_*.py` | Typed client classes with start/execute/signal/query methods |
| `requirements.txt` | Python dependencies |
| `Dockerfile` | Container image for the generated app |
| `docker-compose.yml` | Service definitions for each worker |

### 4. Download

Switch to the **Results** tab to see all generated projects. Download individual files or the entire project as a ZIP.

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/generate` | Submit YAML, receive generated project |
| `GET` | `/api/projects` | List all generated projects |
| `GET` | `/api/projects/{id}/files/{name}` | Download a single file |
| `GET` | `/api/projects/{id}/download` | Download project as ZIP |
| `GET` | `/api/health` | Health check |

## Development

```bash
# Backend only (hot reload)
cd backend
pip install -r requirements.txt
uvicorn main:app --reload --port 8000

# Frontend — just open frontend/index.html or use any static server
```

## Extending

The code generator in `backend/main.py` can be extended to support:

- **TypeScript** — Add TS template functions alongside the Python ones
- **Go / Java** — Same pattern, different template output
- **Protobuf** — Generate `.proto` files from the type definitions
- **Interceptors** — Wire OpenTelemetry / Datadog based on worker config
