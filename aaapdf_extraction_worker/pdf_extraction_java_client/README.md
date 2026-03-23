# PDF Extraction – Java 21 Temporal Client

A Java 21 client that starts and awaits the Python `PdfExtractionWorkflow` via Temporal. Uses records, sealed pattern matching, text blocks, and other modern Java features.

## Project Structure

```
src/main/java/com/pdfextraction/
├── config/
│   └── TemporalConfig.java          # ENV-driven config (record)
├── model/
│   ├── PdfExtractionRequest.java    # Input value object (record)
│   └── PdfExtractionResult.java     # Output value object (record)
└── client/
    ├── TemporalClientFactory.java   # Builds WorkflowClient from config
    ├── PdfExtractionWorkflowStub.java  # @WorkflowInterface matching Python
    ├── PdfExtractionService.java    # Sync / async / fire-and-forget APIs
    └── PdfExtractionClient.java     # CLI entry point
```

## Prerequisites

- Java 21+
- Running Temporal server
- Running Python PDF extraction worker (see companion project)

## Quick Start

```bash
# 1. Configure
cp .env.example .env
# Edit .env with your Temporal host if not localhost

# 2. Build
./gradlew build

# 3. Run
./gradlew run --args="--file-name report.pdf --file-location /data/report.pdf"
```

## Usage as a Library

```java
var config  = TemporalConfig.load();
var request = new PdfExtractionRequest("report.pdf", "/data/report.pdf");

try (var service = new PdfExtractionService(config)) {

    // Blocking
    PdfExtractionResult result = service.extractSync(request);

    // Async
    CompletableFuture<PdfExtractionResult> future = service.extractAsync(request);

    // Fire-and-forget (returns workflow ID)
    String workflowId = service.extractFireAndForget(request);
}
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `TEMPORAL_HOST` | `localhost:7233` | Temporal server address |
| `TEMPORAL_NAMESPACE` | `default` | Temporal namespace |
| `TEMPORAL_TASK_QUEUE` | `pdf-extraction-queue` | Must match the Python worker's queue |
