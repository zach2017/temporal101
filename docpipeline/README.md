# Document Processing Pipeline with Temporal

A fault-tolerant document processing pipeline using **Temporal.io** workflows,
with a Flask upload UI and full Docker Compose stack.

## Architecture

```
┌──────────────┐     ┌─────────────────────────────────────────────┐
│  Browser UI  │────▶│  Flask App (:5555)                          │
│  Upload Page │     │  POST /upload → starts Temporal workflow    │
└──────────────┘     │  GET /status/<id> → queries workflow state  │
                     └────────────┬────────────────────────────────┘
                                  │
                     ┌────────────▼────────────────────────────────┐
                     │  Temporal Server (:7233)                     │
                     │  Durable workflow execution engine           │
                     │  Event history in PostgreSQL                 │
                     └────────────┬────────────────────────────────┘
                                  │
                     ┌────────────▼────────────────────────────────┐
                     │  Temporal Worker                             │
                     │  Polls 'document-processing' queue           │
                     │  Runs DocumentProcessingWorkflow            │
                     │  Executes all activity stubs                │
                     └─────────────────────────────────────────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
        LocalStack S3      Elasticsearch        Filesystem
        (:4566)            (:9200)              /app/storage
```

## Pipeline Flow

```
Upload → Detect File Type → Store Raw (S3 + FS parallel)
       → Route by type:
           PDF  → extract text → extract images → batch OCR
           DOCX → extract text
           Text → direct read
           Image → OCR
       → Store Text (S3 + FS + Elasticsearch parallel)
       → Store Images (S3, if PDF)
       → Index Metadata (Elasticsearch)
       → Done
```

## Services

| Service         | Port  | Purpose                        |
|-----------------|-------|--------------------------------|
| Flask App       | 5555  | Upload UI + API                |
| Temporal Server | 7233  | Workflow engine (gRPC)         |
| Temporal UI     | 8080  | Visual workflow debugger        |
| PostgreSQL      | 5432  | Temporal event history          |
| LocalStack S3   | 4566  | S3-compatible object storage    |
| Elasticsearch   | 9200  | Full-text search + metadata     |

## Quick Start

```bash
# 1. Start everything
docker compose up -d --build

# 2. Wait for services (~60s)
docker compose ps

# 3. Open the upload page
open http://localhost:5555

# 4. Open Temporal UI to watch workflows
open http://localhost:8080
```

## Usage

1. Open **http://localhost:5555** in your browser
2. Drag-and-drop or select a file (PDF, DOCX, TXT, PNG, JPG, etc.)
3. Click **Upload & Process**
4. Watch the pipeline progress in real-time
5. View the full result JSON when complete
6. Click **Temporal UI** to see the event history

## API Endpoints

```
POST /upload          Upload file, start workflow
GET  /status/<id>     Query workflow status (live)
GET  /result/<id>     Get final workflow result
GET  /workflows       List all tracked workflows
GET  /health          Health check
```

## Activity Stubs

All activities are **simulated stubs** that return realistic success responses
with brief sleeps to simulate I/O. Replace them with real implementations:

- `detect_file_type` — uses `mimetypes` (real logic)
- `store_document_s3` — stub (swap in `boto3` S3 upload)
- `extract_text_from_pdf` — stub (swap in `PyPDF2`)
- `extract_text_from_docx` — stub (swap in `python-docx`)
- `ocr_single_image` — stub (swap in `pytesseract`)
- `store_text_elasticsearch` — stub (swap in `elasticsearch` client)
- etc.

## Shutdown

```bash
docker compose down      # Stop (keep data)
docker compose down -v   # Full reset (delete volumes)
```
