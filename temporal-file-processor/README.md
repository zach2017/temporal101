# Temporal File Processor

A production-grade **Temporal IO** file-processing pipeline in **Java 21** that detects file types via MIME inspection, extracts text from documents, runs **Tesseract OCR** on images and PDF-embedded images, and persists all results locally.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         Temporal Service                                 │
│  ┌─────────────┐    ┌──────────────────────┐    ┌────────────────────┐  │
│  │  PostgreSQL  │◄──▶│   Temporal Server     │◄──▶│   Temporal UI      │  │
│  │  (persist)   │    │   (orchestrator)      │    │   (port 8080)      │  │
│  └─────────────┘    └──────────┬───────────┘    └────────────────────┘  │
└─────────────────────────────────┼────────────────────────────────────────┘
                                  │ gRPC (7233)
                    ┌─────────────┴─────────────┐
                    │                           │
              ┌─────┴──────┐            ┌───────┴────────┐
              │   Worker    │            │    Client       │
              │  (JRE 21)  │            │   (JRE 21)      │
              │             │            │                 │
              │ ┌─────────┐ │            │ Sync / Async    │
              │ │Tesseract│ │            │ Query / Signal  │
              │ │  OCR    │ │            │ CLI             │
              │ └─────────┘ │            └─────────────────┘
              │ ┌─────────┐ │
              │ │ PDFBox  │ │
              │ └─────────┘ │
              │ ┌─────────┐ │
              │ │  Tika   │ │
              │ └─────────┘ │
              │ ┌─────────┐ │
              │ │Apache POI│ │
              │ └─────────┘ │
              └──────────────┘
```

### Processing Pipeline

```
 Input File
     │
     ▼
 ┌─────────────────┐
 │ Detect MIME type │  (Apache Tika — magic bytes + extension)
 └────────┬────────┘
          │
     ┌────┴────┬──────────┬──────────┬──────────┐
     ▼         ▼          ▼          ▼          ▼
  IMAGE       PDF       WORD      EXCEL     PLAIN_TEXT
     │         │        PPTX        │          │
     │         │          │         │          │
     ▼         ▼          ▼         ▼          ▼
  Tesseract  Extract    Apache    Apache    Read UTF-8
   OCR       Text +     Tika/     Tika/
     │       Images      POI       POI
     │         │
     │    ┌────┴─────┐
     │    │  OCR     │
     │    │  each    │
     │    │  image   │
     │    └────┬─────┘
     │         │
     │    ┌────┴─────┐
     │    │  Merge   │
     │    │  texts   │
     │    └────┬─────┘
     │         │
     └────┬────┘
          ▼
 ┌────────────────┐
 │ Copy to output │
 └────────────────┘
```

## Project Structure

```
temporal-file-processor/
├── pom.xml                           ← Parent POM (Java 21, dependency management)
├── docker-compose.yml                ← Full stack: Temporal + Postgres + Worker
├── Dockerfile.worker                 ← Worker image (JRE 21 + Tesseract)
├── Dockerfile.client                 ← Client image (JRE 21, lightweight)
├── test-e2e.sh                       ← End-to-end Docker smoke test
├── .env                              ← Default environment variables
│
├── file-processor-common/            ← SHARED CONTRACT (client + worker)
│   ├── pom.xml
│   └── src/main/java/com/fileprocessor/
│       ├── model/
│       │   ├── FileProcessingRequest.java    ← Input DTO
│       │   ├── FileProcessingResult.java     ← Output DTO (with Builder)
│       │   ├── MimeDetectionResult.java      ← MIME detection result
│       │   ├── DetectedFileType.java         ← Enum: IMAGE, PDF, WORD, etc.
│       │   └── ExtractedImageInfo.java       ← Per-image OCR metadata
│       ├── workflow/
│       │   └── FileProcessingWorkflow.java   ← @WorkflowInterface
│       ├── activity/
│       │   ├── FileDetectionActivities.java  ← @ActivityInterface
│       │   ├── TextExtractionActivities.java ← @ActivityInterface
│       │   ├── OcrActivities.java            ← @ActivityInterface
│       │   └── FileStorageActivities.java    ← @ActivityInterface
│       └── shared/
│           └── TaskQueues.java               ← Shared constants
│
├── file-processor-worker/            ← WORKER (heavy dependencies)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/fileprocessor/worker/
│       │   ├── FileProcessorWorker.java              ← main() entry point
│       │   ├── workflow/
│       │   │   └── FileProcessingWorkflowImpl.java   ← Orchestration logic
│       │   ├── activity/
│       │   │   ├── FileDetectionActivitiesImpl.java  ← Tika MIME detection
│       │   │   ├── TextExtractionActivitiesImpl.java ← PDFBox + Tika + POI
│       │   │   ├── OcrActivitiesImpl.java            ← Tesseract via Tess4J
│       │   │   └── FileStorageActivitiesImpl.java    ← Filesystem ops
│       │   └── util/
│       │       └── MimeTypeResolver.java             ← MIME → enum mapping
│       ├── main/resources/
│       │   └── logback.xml
│       └── test/java/com/fileprocessor/worker/
│           └── FileProcessingWorkflowTest.java       ← 8 test cases
│
└── file-processor-client/            ← CLIENT (lightweight)
    ├── pom.xml
    └── src/main/java/com/fileprocessor/client/
        ├── FileProcessorClient.java  ← Reusable client library (sync/async/query/signal)
        └── FileProcessorCli.java     ← CLI entry point
```

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 21+ (for local development only)
- Maven 3.9+ (for local development only)

### 1. Start Everything with Docker Compose

```bash
# Start Temporal + PostgreSQL + Worker
docker compose up -d

# Watch worker logs
docker compose logs -f worker
```

Services:
| Service      | Port  | URL                         |
|-------------|-------|-----------------------------|
| Temporal    | 7233  | gRPC                        |
| Temporal UI | 8080  | http://localhost:8080        |
| PostgreSQL  | 5432  | temporal/temporal            |

### 2. Submit a File via CLI

```bash
# Copy a file into the inbox volume
docker cp /path/to/invoice.pdf $(docker compose ps -q worker):/data/inbox/

# Run the client (synchronous — blocks until done)
docker compose run --rm client \
  -f invoice.pdf \
  -l /data/inbox/invoice.pdf \
  -o /data/outbox

# Run async (returns workflow ID immediately)
docker compose run --rm client \
  -f invoice.pdf \
  -l /data/inbox/invoice.pdf \
  -o /data/outbox \
  -m '{"department":"finance"}' \
  --async
```

### 3. Run the E2E Test

```bash
./test-e2e.sh
```

### 4. Tear Down

```bash
docker compose down -v
```

## Local Development (No Docker)

```bash
# 1. Start a local Temporal dev server
temporal server start-dev

# 2. Build all modules
mvn clean package

# 3. Start the worker
cd file-processor-worker
java -jar target/file-processor-worker-1.0.0-SNAPSHOT.jar

# 4. Run the client
cd ../file-processor-client
java -jar target/file-processor-client-1.0.0-SNAPSHOT.jar \
  -f report.pdf \
  -l /path/to/report.pdf \
  -o /tmp/output
```

> **Note:** For local OCR, install Tesseract:
> ```bash
> # macOS
> brew install tesseract
>
> # Ubuntu/Debian
> sudo apt install tesseract-ocr tesseract-ocr-eng
> ```

## Using the Client Library Programmatically

The `file-processor-client` module provides `FileProcessorClient` — a reusable
Java library you can embed in any application:

```java
// Add to your pom.xml:
// <dependency>
//   <groupId>demo.temporal</groupId>
//   <artifactId>file-processor-client</artifactId>
//   <version>1.0.0-SNAPSHOT</version>
// </dependency>

try (FileProcessorClient client = FileProcessorClient.builder()
        .temporalAddress("localhost:7233")
        .namespace("default")
        .build()) {

    // ── Synchronous ─────────────────────────────────────────
    FileProcessingResult result = client.processFileSync(
            "invoice.pdf",
            "/data/inbox/invoice.pdf",
            "/data/outbox",
            Map.of("department", "finance"));

    System.out.println("Success: " + result.isSuccess());
    System.out.println("Output:  " + result.getTextOutputPath());
    System.out.println("Chars:   " + result.getTotalCharacters());

    // ── Asynchronous ────────────────────────────────────────
    FileProcessorClient.AsyncHandle handle = client.processFileAsync(
            "scan.tiff", "/data/inbox/scan.tiff", "/data/outbox", null);

    System.out.println("Workflow ID: " + handle.getWorkflowId());

    // Query status while running
    String status = client.queryStatus(handle.getWorkflowId());
    System.out.println("Status: " + status);

    // Cancel if needed
    // client.cancelProcessing(handle.getWorkflowId());

    // Wait for result
    FileProcessingResult asyncResult = handle.getFuture().join();
}
```

## File Type Support

| Category     | MIME Types                                                    | Strategy               |
|-------------|---------------------------------------------------------------|------------------------|
| IMAGE       | image/jpeg, image/png, image/tiff, image/bmp, image/gif      | Tesseract OCR          |
| PDF         | application/pdf                                               | PDFBox text + image extraction → OCR |
| WORD        | application/msword, .docx, application/rtf                    | Apache Tika/POI        |
| SPREADSHEET | application/vnd.ms-excel, .xlsx                               | Apache Tika/POI        |
| PRESENTATION| application/vnd.ms-powerpoint, .pptx                          | Apache Tika/POI        |
| PLAIN_TEXT  | text/plain, text/csv, text/html, application/json, etc.       | Direct UTF-8 read      |
| UNSUPPORTED | Everything else                                               | Returns error result   |

## Output Directory Structure

For a file named `invoice.pdf`:

```
/tmp/file-processor/
└── invoice/                           ← Main tmp directory
    ├── invoice_pdf_text.txt           ← Text layer from PDF
    ├── invoice_extracted.txt          ← Final merged text
    └── invoice_images/                ← Extracted images subdirectory
        ├── page_1_img_0.png           ← Extracted image
        ├── page_1_img_0.txt           ← OCR text for that image
        ├── page_2_img_0.png
        └── page_2_img_0.txt

/data/outbox/
└── invoice_extracted.txt              ← Final output (copied from tmp)
```

## Configuration

All configuration via environment variables:

| Variable                          | Default                                    | Description                           |
|-----------------------------------|--------------------------------------------|---------------------------------------|
| `TEMPORAL_ADDRESS`                | `localhost:7233`                           | Temporal gRPC endpoint                |
| `TEMPORAL_NAMESPACE`              | `default`                                  | Temporal namespace                    |
| `WORKER_MAX_CONCURRENT_ACTIVITIES`| `5`                                        | Max parallel Activity executions      |
| `TESSDATA_PREFIX`                 | `/usr/share/tesseract-ocr/5/tessdata`      | Tesseract trained data path           |
| `JAVA_OPTS`                       | `-Xms256m -Xmx512m`                       | JVM options                           |

## Running Tests

```bash
# Unit + integration tests (uses Temporal's in-memory test server)
mvn test

# Just the workflow tests
mvn test -pl file-processor-worker -Dtest=FileProcessingWorkflowTest
```

## Temporal Retry & Timeout Configuration

| Activity Type      | Start-to-Close | Heartbeat | Max Attempts | Backoff     |
|-------------------|----------------|-----------|--------------|-------------|
| MIME Detection    | 30s            | —         | 3            | default     |
| Text Extraction   | 10m            | 2m        | 3            | 2s × 2.0   |
| OCR (Tesseract)   | 15m            | 3m        | 3            | 5s × 2.0   |
| File Storage      | 5m             | —         | 3            | default     |

## License

MIT
