# Temporal Document Processor

A PDF-to-text processing pipeline built on **Temporal IO** with a **Python 3.13** worker (FastAPI + temporalio SDK), and a **Tailwind CSS** frontend.

## Architecture

```
┌──────────────┐     POST /api/upload      ┌──────────────────┐
│              │  ─────────────────────────▶│  FastAPI          │
│   Frontend   │                            │  (Python 3.13)   │
│  (HTML/JS)   │  GET /api/download/{id}   │  port 8080       │
│              │  ◀────────────────────────│                  │
└──────────────┘                            └────────┬─────────┘
                                                     │ starts workflow
                                                     ▼
                                            ┌──────────────────┐
                                            │  Temporal Server  │
                                            │  port 7233        │
                                            │  UI: port 8088    │
                                            └────────┬─────────┘
                                                     │ dispatches
                                                     ▼
                                            ┌──────────────────┐
                                            │  Temporal Worker  │
                                            │  (same process)   │
                                            │                  │
                                            │  Activities:      │
                                            │  • extract_text   │
                                            │  • save_text      │
                                            │  • read_text      │
                                            │  • doc_exists     │
                                            └────────┬─────────┘
                                                     │
                                                     ▼
                                            ┌──────────────────┐
                                            │  File System      │
                                            │  ./storage/       │
                                            │  ├── uploads/     │
                                            │  └── documents/   │
                                            └──────────────────┘
```

## Workflows

### 1. PdfProcessingWorkflow
Pipeline: **Upload → Extract Text → Store → Return Reference**

Activities:
1. `extract_text_from_pdf` — Uses PyMuPDF to extract all text
2. `save_text_to_file` — Writes `.txt` file + `metadata.json` to filesystem
3. Returns `DocumentResult` dataclass with file path, char count, doc ID

### 2. DocumentDownloadWorkflow
Pipeline: **Validate → Read → Return Content**

Activities:
1. `document_exists` — Checks metadata.json exists
2. `read_text_file` — Reads extracted text from storage

## Tech Stack

| Component       | Technology                        |
|-----------------|-----------------------------------|
| Workflow Engine | Temporal IO 1.24                  |
| Worker + API    | Python 3.13, temporalio 1.7, FastAPI |
| PDF Extraction  | PyMuPDF (fitz)                    |
| Database        | PostgreSQL 15 (Temporal storage)  |
| Frontend        | HTML, Tailwind CSS, vanilla JS    |
| Container       | Docker Compose                    |

## Quick Start

### Prerequisites
- Docker & Docker Compose

### 1. Start the stack

```bash
cd temporal-doc-processor
docker compose up --build
```

Wait for all services (~30-60s). You'll see:
```
python-worker  | [worker] Connected. Listening on queue: DOC_PROCESSING_QUEUE
```

### 2. Open the frontend

```bash
# Option A: open the file directly
open frontend/index.html

# Option B: serve with Python
cd frontend && python3 -m http.server 3000
# Visit http://localhost:3000
```

### 3. Use it

1. **Upload**: Drag a PDF → click "Process PDF"
2. **Download**: Document ID auto-fills → click "Fetch"
3. **Temporal UI**: http://localhost:8088

## API Endpoints

| Method | Endpoint                     | Description                        |
|--------|------------------------------|------------------------------------|
| POST   | `/api/upload`                | Upload PDF, triggers processing    |
| GET    | `/api/download/{documentId}` | Fetch extracted text via workflow   |
| GET    | `/api/health`                | Health check                       |

### curl examples

```bash
# Upload
curl -X POST http://localhost:8080/api/upload \
  -F "file=@my-document.pdf"

# Download
curl http://localhost:8080/api/download/<document-id>
```

## Project Structure

```
temporal-doc-processor/
├── docker-compose.yml
├── dynamicconfig/
│   └── development-sql.yaml
├── storage/                        # Mounted volume
│   ├── uploads/
│   └── documents/
├── frontend/
│   └── index.html                  # Tailwind CSS frontend
└── python-worker/
    ├── Dockerfile
    ├── requirements.txt
    ├── entrypoint.py               # Runs worker + API concurrently
    ├── api.py                      # FastAPI REST endpoints
    ├── worker.py                   # Standalone worker (alternative)
    ├── workflows.py                # Temporal workflow definitions
    ├── activities.py               # Activity implementations (PyMuPDF)
    └── models.py                   # DocumentResult dataclass
```

## Ports

| Service       | Port  |
|---------------|-------|
| FastAPI       | 8080  |
| Temporal gRPC | 7233  |
| Temporal UI   | 8088  |
| PostgreSQL    | 5432  |

## Stopping

```bash
docker compose down        # Stop containers
docker compose down -v     # Stop + remove volumes
```
