# PDF Extraction Pipeline – Docker Compose

One-command deployment of the entire PDF extraction pipeline with all infrastructure, using **LocalStack** for S3-compatible object storage.

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│  docker compose                                                      │
│                                                                      │
│  ┌─────────────┐  ┌───────────────┐  ┌───────────────────────┐      │
│  │ PostgreSQL   │  │ Elasticsearch │  │ LocalStack (S3)       │      │
│  │ :5432        │  │ :9200         │  │ :4566                 │      │
│  └──────┬───────┘  └──────┬────────┘  └──────┬────────────────┘      │
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
| LocalStack S3 | http://localhost:4566 | S3-compatible API |
| PostgreSQL | localhost:5432 | Temporal persistence |
| Elasticsearch | http://localhost:9200 | Temporal visibility |

## Interacting with LocalStack S3

```bash
# List buckets
aws --endpoint-url http://localhost:4566 s3 ls

# List objects in the processing bucket
aws --endpoint-url http://localhost:4566 s3 ls s3://document-processing/ --recursive

# Download extracted text
aws --endpoint-url http://localhost:4566 \
    s3 cp s3://document-processing/my_report/extracted_text.json -

# Download an extracted image
aws --endpoint-url http://localhost:4566 \
    s3 cp s3://document-processing/my_report/extracted_images/page_1_img_0.png ./
```

You can also use the `awslocal` wrapper if you have `localstack` CLI installed:
```bash
pip install awscli-local
awslocal s3 ls s3://document-processing/ --recursive
```

## Default Credentials

| Service | User | Password |
|---|---|---|
| LocalStack S3 | `test` | `test` |
| PostgreSQL | `temporal` | `temporal_secret_change_me` |

> LocalStack accepts any credentials in the free tier. Change the PostgreSQL password in `.env` before non-local deployment.

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
| `TEMPORAL_HOST` | `temporal-server:7233` | Internal Temporal address |
| `S3_ENDPOINT_URL` | `http://localstack:4566` | Internal LocalStack address |
| `S3_BUCKET_NAME` | `document-processing` | Auto-created on startup |
| `S3_ACCESS_KEY_ID` | `test` | LocalStack accepts any value |
| `S3_SECRET_ACCESS_KEY` | `test` | LocalStack accepts any value |
| `LOCALSTACK_VERSION` | `4.1` | LocalStack image version |
| `LOCALSTACK_PERSISTENCE` | `1` | Persist S3 data across restarts |
| `PDF_SOURCE_DIR` | `./sample_pdfs` | Host dir mounted into workers |
| `MAX_CONCURRENT_ACTIVITIES` | `5` | Per-worker concurrency |

## Switching to Real AWS S3

To point the workers at a real AWS S3 bucket instead of LocalStack, update `.env`:

```env
S3_ENDPOINT_URL=              # leave blank – boto3 uses the default AWS endpoint
S3_BUCKET_NAME=your-production-bucket
S3_ACCESS_KEY_ID=AKIA...
S3_SECRET_ACCESS_KEY=...
S3_REGION=us-east-1
```

No code changes required — the S3 gateway reads everything from environment variables.

## Directory Layout

```
docker-compose/
├── docker-compose.yml        ← orchestration
├── .env                      ← all environment configuration
├── sample_pdfs/              ← drop PDFs here
│
├── ../pdf_extraction_worker/       ← Python worker source + Dockerfile
└── ../pdf_extraction_java_client/  ← Java client source + Dockerfile
```
