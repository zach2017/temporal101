# Document Processing – Java 21 Temporal Client

Publishes `DocumentIntakeWorkflow` executions to the `document-intake-queue`.
The Python intake worker picks up the task, detects the MIME type, and routes
it through the correct extraction pipeline.

## How It Works

```
┌──────────────────────┐       Temporal        ┌──────────────────────────┐
│  Java Client         │ ──── publishes ────▶  │  document-intake-queue   │
│  (this project)      │      workflow to      │                          │
│                      │      the queue        │  Python intake_worker    │
│  DocumentProcessing  │                       │  picks up the task and   │
│  Service.processSync │ ◀─── result ────────  │  routes to PDF / OCR /   │
│                      │                       │  conversion pipeline     │
└──────────────────────┘                       └──────────────────────────┘
```

## Quick Start

```bash
# Build
./gradlew build

# Run — publishes a workflow and waits for the result
./gradlew run --args="--file-name report.pdf --file-location /data/docs/report.pdf"

# With MIME hint (skips auto-detection)
./gradlew run --args="--file-name photo.jpg --file-location /data/docs/photo.jpg --file-type image/jpeg"
```

## Docker

```bash
# From the project root:
docker compose --profile client run java-client \
    --file-name report.pdf \
    --file-location /data/docs/report.pdf
```

## Usage as a Library

```java
var config  = TemporalConfig.load();
var request = new DocumentProcessingRequest("report.pdf", "/data/docs/report.pdf");

try (var service = new DocumentProcessingService(config)) {

    // Blocking — waits for the Python worker to finish
    DocumentProcessingResult result = service.processSync(request);

    // Async — returns CompletableFuture
    CompletableFuture<DocumentProcessingResult> future = service.processAsync(request);

    // Fire-and-forget — returns workflow ID
    String workflowId = service.processFireAndForget(request);
}
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `TEMPORAL_HOST` | `host.docker.internal:7233` | Temporal gRPC address |
| `TEMPORAL_NAMESPACE` | `default` | Temporal namespace |
| `TEMPORAL_TASK_QUEUE` | `document-intake-queue` | Must match the Python intake worker |
