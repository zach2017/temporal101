# Document Processing Pipeline

A **Domain-Driven Design** Temporal worker pipeline that accepts **any document type**, auto-detects its MIME type, and routes it through the correct extraction pipeline to produce searchable text stored in S3.

## Architecture

```
                         ┌──────────────────────────┐
                         │   start_workflow CLI      │
                         │   or Java Client          │
                         └────────────┬─────────────┘
                                      │
                         ┌────────────▼─────────────┐
                         │  DocumentIntakeWorkflow   │
                         │  (document-intake-queue)  │
                         │                           │
                         │  1. detect_mime_type       │
                         │  2. route by category      │
                         └──┬──────────┬──────────┬──┘
                            │          │          │
              ┌─────────────▼──┐  ┌────▼────┐  ┌──▼──────────────────┐
              │  PDF            │  │  IMAGE  │  │  OFFICE / TEXT /    │
              │  Extraction     │  │  OCR    │  │  HTML / RTF / EPUB  │
              │  Workflow       │  │  Wkflow │  │  Conversion Wkflow  │
              │                 │  │         │  │                     │
              │ pdf-extraction  │  │ image-  │  │ document-conversion │
              │ -queue          │  │ ocr-    │  │ -queue              │
              │                 │  │ queue   │  │                     │
              │ ┌─────────────┐│  │         │  │ convert_document    │
              │ │extract text ││  │ upload  │  │ _to_text            │
              │ │extract imgs ││  │ → OCR   │  │                     │
              │ │store to S3  ││  │ → store │  │ store to S3         │
              │ └──────┬──────┘│  │         │  └─────────────────────┘
              │        │       │  │         │
              │  ┌─────▼─────┐ │  │         │
              │  │ImageOcr   │◄┘  │         │
              │  │Workflow   │◄───┘         │
              │  │(shared)   │              │
              │  └───────────┘              │
              └─────────────────────────────┘
                            │
                   ┌────────▼────────┐
                   │   LocalStack    │
                   │   S3 Bucket     │
                   │                 │
                   │ <doc>/          │
                   │   extracted_    │
                   │     text.json   │
                   │   extracted_    │
                   │     images/     │
                   └─────────────────┘
```

## Supported Document Types

| Category | MIME Types | Handler |
|---|---|---|
| **PDF** | `application/pdf` | PdfExtractionWorkflow → text + images + OCR |
| **Image** | `image/png`, `jpeg`, `tiff`, `bmp`, `gif`, `webp`, `heic` | ImageDocumentWorkflow → OCR → text |
| **Word** | `.docx`, `.doc`, `.odt` | DocumentConversionWorkflow |
| **Spreadsheet** | `.xlsx`, `.xls`, `.ods`, `.csv`, `.tsv` | DocumentConversionWorkflow |
| **Presentation** | `.pptx`, `.ppt`, `.odp` | DocumentConversionWorkflow |
| **HTML/XML** | `text/html`, `application/xhtml+xml` | DocumentConversionWorkflow |
| **Plain text** | `.txt`, `.md`, `.rst`, `.log` | DocumentConversionWorkflow |
| **Rich text** | `.rtf` | DocumentConversionWorkflow |
| **Ebook** | `.epub` | DocumentConversionWorkflow |
| **Email** | `.eml` | DocumentConversionWorkflow |

## Project Structure

```
document_processing_worker/
├── domain/
│   ├── value_objects/
│   │   └── documents.py          # DocumentFileReference (with file_type),
│   │                              # DocumentCategory, MimeDetectionResult,
│   │                              # ExtractedText, ExtractedImage, etc.
│   └── services/
│       ├── mime_detection.py      # MIME detection + categorisation
│       ├── pdf_processing.py      # PyMuPDF text + image extraction
│       └── document_conversion.py # docx/xlsx/pptx/html/rtf/epub/email → text
│
├── application/
│   ├── workflows/
│   │   ├── document_intake_workflow.py     # Top-level router
│   │   ├── pdf_extraction_workflow.py      # PDF → text + images + OCR
│   │   ├── image_document_workflow.py      # Image → OCR → text
│   │   ├── image_ocr_workflow.py           # Shared OCR child workflow
│   │   └── document_conversion_workflow.py # Office/text/HTML → text
│   └── activities/
│       ├── mime_activities.py       # MIME detection activity
│       ├── pdf_activities.py        # PDF extraction + S3 storage
│       ├── ocr_activities.py        # Image-to-text (OCR stub)
│       └── conversion_activities.py # Document conversion activity
│
├── infrastructure/
│   ├── config.py              # ENV-driven settings (pydantic-settings)
│   └── s3/gateway.py          # boto3 S3 adapter
│
├── worker/
│   ├── intake_worker.py       # document-intake-queue
│   ├── pdf_worker.py          # pdf-extraction-queue
│   ├── ocr_worker.py          # image-ocr-queue
│   ├── conversion_worker.py   # document-conversion-queue
│   └── start_workflow.py      # CLI client
│
├── Dockerfile                 # Multi-stage (4 targets)
├── requirements.txt
└── .env.example

docker-compose/
├── docker-compose.yml
├── .env
└── sample_docs/               # Drop files here
```

## Workers & Task Queues

| Worker | Task Queue | Workflows | Activities |
|---|---|---|---|
| `intake_worker` | `document-intake-queue` | DocumentIntakeWorkflow | detect_mime_type |
| `pdf_worker` | `pdf-extraction-queue` | PdfExtractionWorkflow | extract_text, extract_images, store_text, store_image, build_ocr_requests |
| `ocr_worker` | `image-ocr-queue` | ImageOcrWorkflow, ImageDocumentWorkflow | ocr_extract_text, upload_image_for_ocr, store_text |
| `conversion_worker` | `document-conversion-queue` | DocumentConversionWorkflow | convert_document_to_text, store_text |

## Docker Image Versions

| Service | Image | Version |
|---|---|---|
| Temporal Server | `temporalio/auto-setup` | 1.28.3 |
| Temporal UI | `temporalio/ui` | 2.36.0 |
| PostgreSQL | `postgres` | 17-alpine |
| Elasticsearch | `elasticsearch` | 8.17.4 |
| LocalStack | `localstack/localstack` | 4.14.0 |
| Python base | `python` | 3.13-slim |
| AWS CLI (init) | `amazon/aws-cli` | latest |

## Quick Start

```bash
cd docker-compose

# 1. Drop documents into the sample directory
mkdir -p sample_docs
cp /path/to/report.pdf sample_docs/
cp /path/to/spreadsheet.xlsx sample_docs/
cp /path/to/photo.jpg sample_docs/

# 2. Start everything
docker compose up -d

# 3. Check health
docker compose ps

# 4. Process a PDF
docker compose exec pdf-worker \
    python -m worker.start_workflow \
    --file-name report.pdf \
    --file-location /data/docs/report.pdf

# 5. Process a Word doc (auto-routes through MIME detection)
docker compose exec intake-worker \
    python -m worker.start_workflow \
    --file-name spreadsheet.xlsx \
    --file-location /data/docs/spreadsheet.xlsx

# 6. Process an image (auto-routes to OCR)
docker compose exec intake-worker \
    python -m worker.start_workflow \
    --file-name photo.jpg \
    --file-location /data/docs/photo.jpg
```

## Service Endpoints

| Service | URL | Purpose |
|---|---|---|
| Temporal UI | http://localhost:8080 | Workflow monitoring |
| Temporal gRPC | localhost:7233 | SDK connections |
| LocalStack S3 | http://localhost:4566 | S3-compatible API |
| PostgreSQL | localhost:5432 | Temporal persistence |
| Elasticsearch | http://localhost:9200 | Temporal visibility |

## Browsing Extracted Data

```bash
# List all processed documents
aws --endpoint-url http://localhost:4566 s3 ls s3://document-processing/ --recursive

# Download extracted text
aws --endpoint-url http://localhost:4566 \
    s3 cp s3://document-processing/report/extracted_text.json -

# Download an extracted image
aws --endpoint-url http://localhost:4566 \
    s3 cp s3://document-processing/report/extracted_images/page_1_img_0.png ./
```

## Scaling Workers

```bash
# Scale OCR workers to handle image-heavy workloads
docker compose up -d --scale ocr-worker=3

# Scale conversion workers for batch office doc processing
docker compose up -d --scale conversion-worker=2
```

## Switching to Real AWS S3

Update `.env` — no code changes required:

```env
S3_ENDPOINT_URL=              # blank = real AWS
S3_BUCKET_NAME=your-production-bucket
S3_ACCESS_KEY_ID=AKIA...
S3_SECRET_ACCESS_KEY=...
S3_REGION=us-east-1
```

## Implementing the OCR Worker

The OCR activity in `application/activities/ocr_activities.py` is a stub.
Replace with your engine of choice — commented examples for Tesseract
and Amazon Textract are included in the file.

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `TEMPORAL_HOST` | `temporal-server:7233` | Temporal server address |
| `TEMPORAL_NAMESPACE` | `default` | Temporal namespace |
| `TEMPORAL_TASK_QUEUE` | `document-intake-queue` | Intake worker queue |
| `TEMPORAL_PDF_TASK_QUEUE` | `pdf-extraction-queue` | PDF worker queue |
| `TEMPORAL_OCR_TASK_QUEUE` | `image-ocr-queue` | OCR worker queue |
| `TEMPORAL_CONVERSION_TASK_QUEUE` | `document-conversion-queue` | Conversion worker queue |
| `S3_ENDPOINT_URL` | `http://localstack:4566` | S3 endpoint |
| `S3_BUCKET_NAME` | `document-processing` | Target S3 bucket |
| `S3_ACCESS_KEY_ID` | `test` | AWS/LocalStack access key |
| `S3_SECRET_ACCESS_KEY` | `test` | AWS/LocalStack secret key |
| `S3_REGION` | `us-east-1` | AWS region |
| `MAX_CONCURRENT_ACTIVITIES` | `5` | Per-worker concurrency |
| `DOCS_SOURCE_DIR` | `./sample_docs` | Host dir mounted into workers |
