# PDF Extraction Temporal Worker

A **Domain-Driven Design** Temporal worker pipeline that extracts text and images from PDF documents, stores them in S3, and dispatches OCR on extracted images via a separate child workflow / worker.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Bounded Context                              │
│                  "PDF Document Processing"                      │
│                                                                 │
│  domain/                                                        │
│  ├── value_objects/   PdfFileReference, ExtractedText,          │
│  │                    ExtractedImage, ImageOcrRequest/Result     │
│  └── services/        PdfProcessingService (pure logic)         │
│                                                                 │
│  application/                                                   │
│  ├── workflows/       PdfExtractionWorkflow (orchestrator)      │
│  │                    ImageOcrWorkflow      (child, stub)       │
│  └── activities/      pdf_activities   (I/O: extract + S3)      │
│                       ocr_activities   (I/O: OCR stub)          │
│                                                                 │
│  infrastructure/                                                │
│  ├── config.py        ENV-driven settings (pydantic-settings)   │
│  └── s3/gateway.py    boto3 adapter                             │
│                                                                 │
│  worker/                                                        │
│  ├── pdf_worker.py    Main worker  (pdf-extraction-queue)       │
│  ├── ocr_worker.py    OCR worker   (image-ocr-queue)            │
│  └── start_workflow.py CLI to trigger a run                     │
└─────────────────────────────────────────────────────────────────┘
```

## Workflow Pipeline

```
start_workflow.py
  │
  ▼
PdfExtractionWorkflow  (pdf-extraction-queue)
  ├── extract_text_from_pdf       → dict of page→text
  ├── store_extracted_text_to_s3  → s3://<bucket>/<doc>/extracted_text.json
  ├── extract_images_from_pdf     → list of image metadata
  ├── store_image_to_s3           → s3://<bucket>/<doc>/extracted_images/page_N_img_M.ext
  ├── build_ocr_requests          → list of OCR payloads
  └── for each image:
        └── child: ImageOcrWorkflow  (image-ocr-queue)
                    └── ocr_extract_text_from_image  (STUB)
```

## S3 Bucket Layout

```
<S3_BUCKET_NAME>/
├── <document_name>/
│   ├── extracted_text.json
│   └── extracted_images/
│       ├── page_1_img_0.png
│       ├── page_1_img_1.jpeg
│       └── page_3_img_0.png
```

## Quick Start

### 1. Prerequisites

- Python 3.11+
- Running Temporal server (`temporal server start-dev`)
- S3-compatible storage (AWS S3 or MinIO for local dev)

### 2. Install

```bash
pip install -r requirements.txt
```

### 3. Configure

```bash
cp .env.example .env
# Edit .env with your Temporal host, S3 credentials, etc.
```

### 4. Run Workers

Terminal 1 – PDF extraction worker:
```bash
python -m worker.pdf_worker
```

Terminal 2 – OCR worker (stub):
```bash
python -m worker.ocr_worker
```

### 5. Start a Workflow

```bash
python -m worker.start_workflow \
    --file-name report.pdf \
    --file-location /path/to/report.pdf
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `TEMPORAL_HOST` | `localhost:7233` | Temporal server address |
| `TEMPORAL_NAMESPACE` | `default` | Temporal namespace |
| `TEMPORAL_TASK_QUEUE` | `pdf-extraction-queue` | PDF worker task queue |
| `TEMPORAL_OCR_TASK_QUEUE` | `image-ocr-queue` | OCR worker task queue |
| `S3_ENDPOINT_URL` | _(none)_ | S3 endpoint (set for MinIO) |
| `S3_BUCKET_NAME` | `document-processing` | Target S3 bucket |
| `S3_ACCESS_KEY_ID` | | AWS/MinIO access key |
| `S3_SECRET_ACCESS_KEY` | | AWS/MinIO secret key |
| `S3_REGION` | `us-east-1` | AWS region |
| `MAX_CONCURRENT_ACTIVITIES` | `5` | Worker concurrency |
| `ACTIVITY_HEARTBEAT_TIMEOUT_SECONDS` | `120` | Activity heartbeat |
| `ACTIVITY_START_TO_CLOSE_TIMEOUT_SECONDS` | `600` | Activity timeout |

## Implementing the OCR Worker

The OCR activity in `application/activities/ocr_activities.py` is a stub. Replace the placeholder with your engine of choice. The file contains commented examples for both **Tesseract** and **Amazon Textract**.
