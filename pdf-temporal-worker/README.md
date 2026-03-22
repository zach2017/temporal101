# PDF-to-Text Temporal Service

A cross-language Temporal service: **Python worker** extracts text and images
from PDFs with constant-memory streaming, **Java CLI client** submits jobs.

```
┌──────────────────┐        ┌────────────────┐        ┌──────────────────┐
│   Java CLI       │  gRPC  │    Temporal     │  poll  │  Python Worker   │
│   Client         │───────►│    Server       │◄───────│  (page-by-page)  │
│                  │        │   :7233 / :8233 │        │                  │
└──────────────────┘        └───────┬────────┘        └──────┬───────────┘
                                    │                        │
                              ┌─────▼─────┐   ┌─────────────┼──────────────┐
                              │ PostgreSQL │   ▼             ▼              ▼
                              │   :5432    │  AWS S3       NFS Dir       HTTP URL
                              └───────────┘ (fetch/put)  (cp/write)    (GET/POST)
```

## Quick Start

```bash
# 1. Start PostgreSQL + Temporal server + Python worker
docker compose up -d

# 2. Wait for healthy (check UI at http://localhost:8233)
docker compose logs -f worker          # watch for "Listening on queue"

# 3. Submit a job via the Java CLI
# ── S3 example ──
docker compose run --rm cli \
    report.pdf  S3  s3://my-bucket/documents

# ── NFS example (uses shared 'pdf-data' volume) ──
cp /path/to/spec.pdf ./test-data/
docker compose run --rm -v ./test-data:/data cli \
    spec.pdf  NFS  /data

# ── URL example ──
docker compose run --rm cli \
    paper.pdf  URL  https://files.example.com/incoming
```

## CLI Usage

```
pdf-client <file_name> <S3|NFS|URL> <location> [options]

Arguments:
  file_name      Name of the PDF file
  storage_type   S3, NFS, or URL
  location       s3://bucket/prefix | /nfs/path | https://base-url

Options:
  --no-images    Skip image extraction
  --host h:p     Temporal server (default: 127.0.0.1:7233)
```

## Storage Behaviour

| Type | Fetch                          | Text output                    | Images output                        |
|------|--------------------------------|--------------------------------|--------------------------------------|
| S3   | `GET s3://bucket/prefix/f.pdf` | `PUT s3://bucket/prefix/f.txt` | `PUT s3://bucket/prefix/f_images/*`  |
| NFS  | `cp /dir/f.pdf`                | `cp → /dir/f.txt`             | `cp → /dir/f_images/*`              |
| URL  | `GET <location>/f.pdf`         | `POST <location>/f.txt`       | `POST <location>/f.pdf` (multipart) |

## Memory Design (Python Worker)

The worker is designed for **large PDFs (hundreds of pages, embedded images)**
without proportional memory growth:

1. **`yield_pages()` generator** — opens the PDF via PyMuPDF and yields one
   `PageResult` at a time. After each yield the page object and image bytes
   are dereferenced so the GC can reclaim them.

2. **Disk-backed text accumulation** — extracted text is written line-by-line
   to a temp file via buffered I/O (`os.fdopen(..., buffering=8192)`), not
   concatenated into a Python string.

3. **Immediate image flush** — each page's images are written to disk and
   uploaded to storage within the same loop iteration, then the local file
   is deleted before advancing to the next page.

4. **Inline text cap** — the `PdfProcessingResult.text_content` field carries
   only the first ~4 KB as a preview; the full text lives at
   `text_storage_path`.

Peak memory ≈ **one PDF page + one page's images + I/O buffers** regardless
of total document size.

## Project Structure

```
pdf-temporal-worker/
├── docker-compose.yml
├── python_worker/
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── models.py            # Shared dataclasses (mirrors Java models)
│   ├── pdf_processor.py     # Generator-based page-at-a-time extractor
│   ├── storage.py           # S3 / NFS / URL handlers
│   ├── activities.py        # Temporal activities (streaming)
│   ├── workflows.py         # Temporal workflow definition
│   └── worker.py            # Entry point
└── java_client/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/java/com/pdfworker/
        ├── model/
        │   ├── StorageType.java
        │   ├── PdfProcessingRequest.java
        │   ├── ExtractedImage.java
        │   └── PdfProcessingResult.java
        ├── workflow/
        │   └── PdfToTextWorkflow.java   # Workflow interface stub
        └── client/
            └── PdfToTextClient.java     # CLI entry point
```

## Cross-Language Contract

The Java client and Python worker share these invariants:

| Constant       | Value                |
|----------------|----------------------|
| Workflow name  | `PdfToTextWorkflow`  |
| Task queue     | `pdf-to-text-queue`  |
| JSON naming    | `snake_case`         |

Model field names are identical across both languages. Jackson
`SNAKE_CASE` naming on the Java side maps directly to Python
dataclass field names.
