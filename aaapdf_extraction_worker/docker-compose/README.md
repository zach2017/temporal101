# PDF Extraction Pipeline – Docker Compose

One-command deployment of the entire PDF extraction pipeline with all infrastructure.

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│  docker compose                                                      │
│                                                                      │
│  ┌─────────────┐  ┌───────────────┐  ┌───────────────┐              │
│  │ PostgreSQL   │  │ Elasticsearch │  │ MinIO (S3)    │              │
│  │ :5432        │  │ :9200         │  │ :9000 / :9001 │              │
│  └──────┬───────┘  └──────┬────────┘  └──────┬────────┘              │
│         │                 │                   │                       │
│  ┌──────┴─────────────────┴───┐               │                      │
│  │  Temporal Server  :7233    │               │                      │
│  └──────┬─────────────────────┘               │                      │
│         │                                     │                      │
│  ┌──────┴──────────┐                          │                      │
│  │  Temporal UI     │                         │                      │
│  │  :8080           │                         │                      │
│  └─────────────────┘                          │                      │
│         │                                     │                      │
│  ┌──────┴──────────┐  ┌──────────────┐        │                      │
│  │  pdf-worker     │  │  ocr-worker  │ ───────┤                      │
│  │  (extraction)   │  │  (OCR stub)  │        │                      │
│  └─────────────────┘  └──────────────┘        │                      │
│                                               │                      │
│  ┌──────────────────┐                         │                      │
│  │  java-client     │─────────────────────────┘                      │
│  │  (on-demand)     │                                                │
│  └──────────────────┘                                                │
└──────────────────────────────────────────────────────────────────────┘
```

## Quick Start

```bash
# 1. Place PDFs in sample directory
mkdir -p sample_pdfs
cp /path/to/your/report.pdf sample_pdfs/

# 2. Start all infrastructure + workers
docker compose up -d

# 3. Wait for everything to be healthy
docker compose ps

# 4. Trigger a workflow (option A – Java client)
docker compose --profile client run java-client \
    --file-name report.pdf \
    --file-location /data/pdfs/report.pdf

# 5. Trigger a workflow (option B – Python CLI)
docker compose exec pdf-worker \
    python -m worker.start_workflow \
    --file-name report.pdf \
    --file-location /data/pdfs/report.pdf
```

## Service Endpoints

| Service | URL | Purpose |
|---|---|---|
| Temporal UI | http://localhost:8080 | Workflow monitoring |
| Temporal gRPC | localhost:7233 | SDK connections |
| MinIO Console | http://localhost:9001 | S3 bucket browser |
| MinIO API | http://localhost:9000 | S3-compatible API |
| PostgreSQL | localhost:5432 | Temporal persistence |
| Elasticsearch | http://localhost:9200 | Temporal visibility |

## Default Credentials

| Service | User | Password |
|---|---|---|
| MinIO | `minioadmin` | `minio_secret_change_me` |
| PostgreSQL | `temporal` | `temporal_secret_change_me` |

> Change these in `.env` before any non-local deployment.

## Common Operations

```bash
# View logs for all workers
docker compose logs -f pdf-worker ocr-worker

# Scale OCR workers
docker compose up -d --scale ocr-worker=3

# Rebuild after code changes
docker compose build pdf-worker ocr-worker
docker compose up -d pdf-worker ocr-worker

# Full teardown (preserves volumes)
docker compose down

# Full teardown including data
docker compose down -v
```

## Configuration

All settings live in `.env`. Key variables:

| Variable | Default | Description |
|---|---|---|
| `TEMPORAL_HOST` | `temporal:7233` | Internal Temporal address |
| `S3_ENDPOINT_URL` | `http://minio:9000` | Internal MinIO address |
| `S3_BUCKET_NAME` | `document-processing` | Auto-created on startup |
| `S3_ACCESS_KEY_ID` | `minioadmin` | MinIO access key |
| `S3_SECRET_ACCESS_KEY` | `minio_secret_change_me` | MinIO secret key |
| `PDF_SOURCE_DIR` | `./sample_pdfs` | Host dir mounted into workers |
| `MAX_CONCURRENT_ACTIVITIES` | `5` | Per-worker concurrency |

## Directory Layout

```
docker-compose/
├── docker-compose.yml    ← this file
├── .env                  ← all environment configuration
├── sample_pdfs/          ← drop PDFs here
│
├── ../pdf_extraction_worker/   ← Python worker source + Dockerfile
└── ../pdf_extraction_java_client/  ← Java client source + Dockerfile
```
