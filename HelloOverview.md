# How Temporal Works

## From Hello World to Document Processing Pipelines

*Workers, Clients, Fault Tolerance & Real-World Patterns*

`temporalio SDK · Python · Java · Spring Boot · docs.temporal.io`

---

## Table of Contents

- [What is Temporal?](#what-is-temporal)
- [Architecture Overview](#architecture-overview)
- [The Hello World Demo](#the-hello-world-demo)
- [Step 1 — Define an Activity](#step-1--define-an-activity)
- [Step 2 — Define a Workflow](#step-2--define-a-workflow)
- [Step 3 — Register a Worker](#step-3--register-a-worker)
- [Step 4 — Start a Workflow from a Client](#step-4--start-a-workflow-from-a-client)
- [Workers in Every Language — Java & Go Examples](#workers-in-every-language--java--go-examples)
- [Three Workers, One Pattern](#three-workers-one-pattern)
- [Python vs. Java vs. Go Workers — Pros & Cons](#python-vs-java-vs-go-workers--pros--cons)
- [Fault Tolerance](#fault-tolerance)
- [Workflow Lifecycle](#workflow-lifecycle)
- [Real-World Case — Document Processing Pipeline](#real-world-case--document-processing-pipeline)
- [The Pipeline — 6 Stages](#the-pipeline--6-stages)
- [The Problem — CompletableFuture Approach](#the-problem--completablefuture-approach)
- [The Solution — Temporal Workflow](#the-solution--temporal-workflow)
- [Activity Definitions — Each Stage Isolated](#activity-definitions--each-stage-isolated)
- [Heartbeats in Activities](#heartbeats-in-activities)
- [Caching Data Between Activities](#caching-data-between-activities)
- [Caching Patterns in Practice](#caching-patterns-in-practice)
- [Side-by-Side — CompletableFuture vs. Temporal](#side-by-side--completablefuture-vs-temporal)
- [The Spring Boot Controller — Before and After](#the-spring-boot-controller--before-and-after)
- [What Happens When Things Fail](#what-happens-when-things-fail)
- [Document Pipeline — Architecture Diagram](#document-pipeline--architecture-diagram)
- [Worker Registration — Spring Boot Integration](#worker-registration--spring-boot-integration)
- [Configuration](#configuration)
- [Scaling as Clients Multiply](#scaling-as-clients-multiply)
- [Scaling Clients — Many Entry Points](#scaling-clients--many-entry-points)
- [Production Scaling Patterns](#production-scaling-patterns)
- [Architecture at Scale](#architecture-at-scale)
- [Key Takeaways](#key-takeaways)
- [References](#references)

---

## What is Temporal?

*A durable execution platform that ensures your code runs to completion — no matter what.*

### 🔁 Workflows

Durable orchestrators that define your business logic. Temporal persists their full event history for replay.

### ⚙️ Activities

Units of work — HTTP calls, DB writes, I/O. Allowed to be non-deterministic. Retried automatically on failure.

### 👷 Workers

Long-running processes that poll a Task Queue, execute Workflows & Activities, and report results to Temporal.

> Temporal Server manages state, scheduling, and retries — your code stays clean.

---

## Architecture Overview

*How the Temporal Server connects Clients and Workers via Task Queues*

```
┌─────────────────────┐          ┌─────────────────────────┐          ┌──────────────────┐
│       Client        │          │     Temporal Server      │          │      Worker       │
│                     │   gRPC   │                          │   gRPC   │                   │
│  run_workflow.py    │ ───────► │     localhost:7233       │ ───────► │    worker.py      │
│  LongRunningCLI.java│          │                          │          │                   │
└─────────────────────┘          └────────────┬────────────┘          └──────────────────┘
                                              │
                                              ▼
                                 ┌────────────────────────┐
                                 │ 📋 Task Queue:          │
                                 │ "hello-world-queue"     │
                                 └────────────────────────┘
```

| Step | Description |
|------|-------------|
| **1. Client starts workflow** | Client sends a gRPC request to the Temporal Server |
| **2. Server queues tasks** | Server places workflow/activity tasks on the Task Queue |
| **3. Worker polls & executes** | Worker picks up tasks, runs the code, reports results |

> Clients and Workers never talk directly — Temporal Server mediates all communication via gRPC.

---

## The Hello World Demo

*7 files across Python and Java that show every Temporal primitive in action*

| File | Role | Description |
|------|------|-------------|
| `activities.py` | Activity Definition | `@activity.defn` — `say_hello()` |
| `workflows.py` | Workflow Definition | `@workflow.defn` — orchestrates activities |
| `worker.py` | Worker Bootstrap | Connects, registers, polls task queue |
| `run_workflow.py` | Python Client | Starts workflow & gets result |
| `LongRunningWorkflow.java` | Java Workflow Interface | `@WorkflowInterface` stub |
| `LongRunningCLI.java` | Java CLI Client | start, start-async, status, result |

> Python runs the Worker (server-side). Java provides a CLI client. Both talk to the same Temporal Server.

---

## Step 1 — Define an Activity

📄 **`activities.py`**

```python
from temporalio import activity

@activity.defn
async def say_hello(name: str) -> str:
    print(f"[Activity] Running say_hello for: {name}")
    return f"Hello, {name}!"
```

| Concept | Detail |
|---------|--------|
| **`@activity.defn`** | Registers the function as a Temporal Activity with the default name `"say_hello"` |
| **`async def`** | Activities can be async or sync. Async is recommended for the Python SDK |
| **Return value** | Must be serializable. Recorded in the Workflow execution event history |

> Activities are where all side-effects live — HTTP calls, database writes, file I/O. They can be retried independently.

📖 [Python SDK → Activity Definition](https://docs.temporal.io/develop/python/core-application#develop-activities)

---

## Step 2 — Define a Workflow

📄 **`workflows.py`**

```python
from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from activities import say_hello

@workflow.defn
class HelloWorldWorkflow:
    @workflow.run
    async def run(self, name: str) -> str:
        result = await workflow.execute_activity(
            say_hello,
            name,
            start_to_close_timeout=timedelta(seconds=10),
            retry_policy=RetryPolicy(maximum_attempts=3),
        )
        return result
```

| Concept | Detail |
|---------|--------|
| **`@workflow.defn`** | Registers class as a Workflow Definition |
| **`@workflow.run`** | Single entry point — must be async, only one allowed per class |
| **`execute_activity()`** | Schedules activity on the Task Queue and awaits its result |
| **`timedelta` + `RetryPolicy`** | 10s timeout per attempt, 3 max retries with exponential backoff |
| **`imports_passed_through()`** | Bypasses the workflow sandbox to import activity definitions safely |

> Workflows must be deterministic. All I/O goes in Activities. Temporal replays the event history to rebuild state.

📖 [Python SDK → Workflow Definition](https://docs.temporal.io/develop/python/core-application#develop-workflows)

---

## Step 3 — Register a Worker

📄 **`worker.py`**

```python
from temporalio.client import Client
from temporalio.worker import Worker

from workflows import HelloWorldWorkflow
from activities import say_hello

async def main(host: str, task_queue: str):
    client = await Client.connect(host)

    worker = Worker(
        client,
        task_queue=task_queue,
        workflows=[HelloWorldWorkflow],
        activities=[say_hello],
    )

    await worker.run()
```

| Step | What Happens |
|------|-------------|
| **1** | Connect to Temporal server at `localhost:7233` via gRPC |
| **2** | Create a Worker bound to `"hello-world-queue"` |
| **3** | Register `HelloWorldWorkflow` + `say_hello` activity |
| **4** | `worker.run()` starts infinite polling loop until Ctrl+C |

> The Worker is the runtime — it's what actually executes your Workflows and Activities.

📖 [Python SDK → Run a Worker](https://docs.temporal.io/develop/python/core-application#run-a-dev-worker)

---

## Step 4 — Start a Workflow from a Client

### Python — `run_workflow.py`

```python
client = await Client.connect(host)

result = await client.execute_workflow(
    HelloWorldWorkflow.run,
    name,
    id=f"hello-{name}-workflow",
    task_queue=task_queue,
)

print(f"Workflow result: {result}")
```

`execute_workflow()` starts the workflow **and** blocks until the result is returned.

📖 [Python SDK → Start Workflow Execution](https://docs.temporal.io/develop/python/temporal-client#start-workflow-execution)

### Java — `LongRunningCLI.java`

```java
// Synchronous start — blocks until result
var result = wf.run(payload);

// Asynchronous start — returns immediately
WorkflowClient.start(wf::run, payload);

// Attach to existing workflow and get result
WorkflowStub stub = client.newUntypedWorkflowStub(
    workflowId, Optional.empty(), Optional.empty()
);
var result = stub.getResult(String.class);
```

The Java CLI offers four commands: `start`, `start-async`, `status`, and `result`.

📖 [Java SDK → Temporal Client](https://docs.temporal.io/develop/java/temporal-client)

> **Cross-language interop:** The Java CLI starts the same workflows that the Python worker executes. Temporal's gRPC + protobuf protocol makes this seamless — no shared code needed.

---

## Workers in Every Language — Java & Go Examples

*Same concepts, same Temporal Server — different SDK idioms*

### ☕ Java Worker

```java
// Connect to Temporal
WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
WorkflowClient client = WorkflowClient.newInstance(service);

// Create Worker on the task queue
WorkerFactory factory = WorkerFactory.newInstance(client);
Worker worker = factory.newWorker("hello-world-queue");

// Register workflow + activity implementations
worker.registerWorkflowImplementationTypes(HelloWorkflowImpl.class);
worker.registerActivitiesImplementations(new GreetActivityImpl());

// Start polling
factory.start();
```

📖 [Java SDK → Run a Worker](https://docs.temporal.io/develop/java/core-application#run-a-dev-worker)

### 🐹 Go Worker

```go
// Connect to Temporal
c, err := client.Dial(client.Options{
    HostPort: "localhost:7233",
})
if err != nil {
    log.Fatal("Unable to connect", err)
}
defer c.Close()

// Create Worker on the task queue
w := worker.New(c, "hello-world-queue", worker.Options{})

// Register workflow + activity functions
w.RegisterWorkflow(HelloWorkflow)
w.RegisterActivity(Greet)

// Start polling (blocks until interrupt signal)
err = w.Run(worker.InterruptCh())
if err != nil {
    log.Fatal("Worker failed", err)
}
```

📖 [Go SDK → Run a Worker](https://docs.temporal.io/develop/go/core-application#run-a-dev-worker)

> All three SDKs share the same pattern: connect → create worker → register types → run. Same server, same Task Queue.

---

## Three Workers, One Pattern

*Every SDK follows the same lifecycle: Connect → Create Worker → Register Types → Run*

### Side-by-Side Comparison

| Step | 🐍 Python | ☕ Java | 🐹 Go |
|------|----------|--------|-------|
| **Connect** | `Client.connect(host)` | `WorkflowServiceStubs.newLocalServiceStubs()` → `WorkflowClient.newInstance(service)` | `client.Dial(client.Options{HostPort: "..."})` |
| **Create Worker** | `Worker(client, task_queue="queue")` | `WorkerFactory.newInstance(client)` → `factory.newWorker("queue")` | `worker.New(c, "queue", worker.Options{})` |
| **Register Workflows** | `workflows=[HelloWorldWorkflow]` | `worker.registerWorkflowImplementationTypes(Impl.class)` | `w.RegisterWorkflow(HelloWorkflow)` |
| **Register Activities** | `activities=[say_hello]` | `worker.registerActivitiesImplementations(new Impl())` | `w.RegisterActivity(Greet)` |
| **Run** | `await worker.run()` | `factory.start()` | `w.Run(worker.InterruptCh())` |

### Polyglot Workers on the Same Task Queue

All three SDKs can poll the **same Task Queue simultaneously**. Temporal distributes tasks to whichever Worker picks them up first. This enables powerful patterns:

```
                    ┌──────────────────────────────┐
                    │      Temporal Server          │
                    │  Task Queue: "doc-pipeline"   │
                    └──┬──────────┬──────────┬─────┘
                       │          │          │
                       ▼          ▼          ▼
                 ┌──────────┐ ┌────────┐ ┌────────┐
                 │ Python   │ │  Java  │ │   Go   │
                 │ Worker   │ │ Worker │ │ Worker │
                 │          │ │        │ │        │
                 │ AI/ML    │ │ Spring │ │ High-  │
                 │ Activities│ │ Boot   │ │ thru-  │
                 │ (PyTorch)│ │ (JPA)  │ │ put I/O│
                 └──────────┘ └────────┘ └────────┘
```

> Mix languages per Activity — Python for AI inference, Java for enterprise integrations, Go for high-throughput I/O. The Workflow orchestrator doesn't care which language executes its Activities.

---

## Python vs. Java vs. Go Workers — Pros & Cons

*Choosing the right SDK for your team and workload*

### 🐍 Python

**✅ Pros**

- Fastest prototyping — minimal boilerplate, clean `@activity.defn` / `@workflow.defn` decorators
- Native `async/await` — excellent for I/O-heavy Activities (HTTP calls, DB queries, API calls)
- Rich AI/ML ecosystem — PyTorch, HuggingFace, scikit-learn, LangChain all available in Activities
- Built-in workflow sandbox enforces determinism at import time
- Type hints + dataclasses make Activity inputs/outputs clean and self-documenting

**⚠️ Cons**

- GIL limits CPU parallelism — compute-heavy Activities run slower than Java/Go equivalents
- Slower than JVM or Go for raw throughput on CPU-bound work
- Smaller Temporal community and fewer production examples compared to Java/Go

**🎯 Best for:** AI/ML pipelines, data science workflows, rapid prototyping, teams already using Python

📖 [Python SDK Developer Guide](https://docs.temporal.io/develop/python)

### ☕ Java

**✅ Pros**

- Mature enterprise ecosystem — Spring Boot, JPA, Hibernate, Maven/Gradle all integrate cleanly
- Strong typing catches errors at compile time — Activity and Workflow signatures are type-safe
- JVM is fast, well-optimized, and battle-tested at scale
- Largest Temporal SDK community with the most samples and production case studies
- `temporal-spring-boot-autoconfigure` provides `@WorkflowImpl` / `@ActivityImpl` auto-discovery

**⚠️ Cons**

- Verbose — more boilerplate than Python or Go (interfaces + implementations + stubs)
- Longer startup times due to JVM warmup (relevant for cold-start scenarios)
- Heavier memory footprint per Worker compared to Go

**🎯 Best for:** Enterprise applications, Spring Boot backends, document processing pipelines, teams with Java expertise

📖 [Java SDK Developer Guide](https://docs.temporal.io/develop/java)

### 🐹 Go

**✅ Pros**

- Temporal itself is written in Go — the Go SDK is first-class and always up-to-date
- Fastest startup time and lowest memory consumption per Worker of all three SDKs
- Goroutines provide massive concurrency with tiny per-goroutine overhead (~2 KB stack)
- Compiles to a single static binary — simple deployment, no runtime dependencies
- Excellent choice for infrastructure and platform teams building internal tooling

**⚠️ Cons**

- No generics in Activity/Workflow function signatures (must use interface types for complex payloads)
- Smaller enterprise library ecosystem compared to Java (no Spring equivalent)
- Error handling verbosity — `if err != nil` patterns throughout Worker and Activity code

**🎯 Best for:** High-throughput Workers, microservices, infrastructure automation, platform engineering

📖 [Go SDK Developer Guide](https://docs.temporal.io/develop/go)

### Quick Decision Matrix

| Factor | Python | Java | Go |
|--------|--------|------|-----|
| **Startup speed** | Medium | Slow (JVM) | Fast |
| **Memory per Worker** | Medium | High | Low |
| **CPU throughput** | Low (GIL) | High | High |
| **I/O concurrency** | High (async) | High (threads) | Very High (goroutines) |
| **Enterprise ecosystem** | Medium | Very High | Medium |
| **AI/ML ecosystem** | Very High | Low | Low |
| **Boilerplate** | Low | High | Medium |
| **Temporal community** | Growing | Largest | Large |
| **Deploy complexity** | pip + venv | JVM + JAR | Single binary |

> **You don't have to choose just one.** Multiple Workers in different languages can poll the same Task Queue. Use Python for AI Activities, Go for high-throughput I/O, and Java for enterprise integrations — all in the same Workflow.

---

## Fault Tolerance

*How Temporal guarantees your workflow completes — no matter what*

### Normal Execution

```
→  say_hello scheduled
→  Worker picks up task
→  Activity completes
→  Result recorded
```

### Worker Crashes 💥

```
→  Process killed mid-run
→  Activity times out
→  Server marks as failed
```

### Automatic Recovery

```
→  Worker restarts
→  Replays event history
→  Skips completed activities
→  Resumes from last checkpoint
```

> **Key:** Temporal persists every event (`ActivityScheduled`, `ActivityCompleted`, etc.) to its database. On replay, completed activities return their cached result — no re-execution. The `RetryPolicy(maximum_attempts=3)` from `workflows.py` handles transient failures automatically.

📖 [Python SDK → Failure Detection](https://docs.temporal.io/develop/python/failure-detection)

---

## Workflow Lifecycle

*Every workflow passes through well-defined states — queryable via the Java CLI or Temporal Web UI*

| Status | Emoji | Description |
|--------|-------|-------------|
| **RUNNING** | 🔄 | Workflow is actively executing Activities |
| **COMPLETED** | ✅ | All Activities finished, result returned |
| **FAILED** | ❌ | Workflow threw an unrecoverable error |
| **CANCELED** | 🚫 | Gracefully canceled by client signal |
| **TERMINATED** | ⛔ | Force-killed from external command |
| **TIMED_OUT** | ⏰ | Exceeded the workflow execution timeout |

These states come from `WorkflowExecutionStatus` in the Temporal protobuf API. `LongRunningCLI.java` maps them to human-readable labels using `describeWorkflowExecution()` and formats timestamps with `Instant.ofEpochMilli()`.

---

## Real-World Case — Document Processing Pipeline

*How Temporal transforms a fragile Spring Boot + CompletableFuture pipeline into a durable, observable, production-grade system*

A typical enterprise document processing pipeline involves multiple long-running, failure-prone stages: uploading files, converting formats, extracting images, running OCR, persisting to databases and search indexes, and performing AI analytics. In a standard Spring Boot application these are chained together with `CompletableFuture` — which works until something goes wrong.

---

## The Pipeline — 6 Stages

```
  📄 Upload        →  📝 Convert to Text  →  🖼️ Extract Images  →  🔍 OCR
  (PDF, DOCX,         (Apache Tika,          (PDFBox image       (Tesseract
   XLSX, images)        Apache POI)            extraction)         OCR engine)
        │                                                              │
        ▼                                                              ▼
  💾 Store in DB    ←──────────────────────────────────────   Merge all text
  📊 Index in                                                  (original +
     Elasticsearch                                              OCR results)
        │
        ▼
  🤖 AI Analytics
  (NLP summarization, entity extraction,
   classification, sentiment analysis)
```

| Stage | What It Does | Failure Modes |
|-------|-------------|---------------|
| **Document Upload** | Accept file via REST, validate MIME type, stage to storage | Network timeout, disk full, invalid format |
| **Convert to Text** | Parse PDF (PDFBox), DOCX (Apache POI), XLSX — extract raw text | Corrupt files, OOM on large documents, encoding issues |
| **Extract Images from PDF** | Pull embedded images from PDF pages for OCR processing | Malformed PDF structure, unsupported image codecs |
| **OCR** | Run Tesseract on extracted images and scanned pages to produce text | Tesseract crash, low-quality scans, timeout on large images |
| **Store in DB + Elasticsearch** | Persist extracted text, metadata, and OCR results | DB connection pool exhaustion, ES cluster unavailable, mapping conflicts |
| **AI Analytics** | NLP summarization, entity extraction, classification, sentiment | Model API timeout, rate limiting, token limit exceeded |

> Every stage can fail independently. The question is: what happens when stage 4 fails after stages 1–3 already completed?

---

## The Problem — CompletableFuture Approach

📄 **`DocumentProcessingService.java` — The Spring Boot Way**

```java
@Service
public class DocumentProcessingService {

    @Async
    public CompletableFuture<ProcessingResult> processDocument(MultipartFile file) {
        return CompletableFuture
            .supplyAsync(() -> uploadAndStage(file))
            .thenApplyAsync(staged -> convertToText(staged))
            .thenApplyAsync(text -> extractImagesFromPdf(text))
            .thenApplyAsync(images -> runOcr(images))
            .thenApplyAsync(ocrResult -> mergeText(ocrResult))
            .thenApplyAsync(fullText -> storeInDbAndElastic(fullText))
            .thenApplyAsync(stored -> runAiAnalytics(stored))
            .exceptionally(ex -> {
                log.error("Pipeline failed: {}", ex.getMessage());
                return ProcessingResult.failed(ex);  // entire pipeline lost
            });
    }
}
```

### What Goes Wrong

| Problem | Impact |
|---------|--------|
| **Server restarts** | The entire `CompletableFuture` chain is in-memory — all progress is lost. A 20-minute OCR job restarts from scratch. |
| **No selective retry** | `exceptionally()` catches everything. If OCR fails, you can't retry just OCR — the whole chain re-runs or fails. |
| **No visibility** | No way to query "what stage is document X in?" without building custom status tracking with a database. |
| **No cancellation** | Once the chain starts, there's no clean way to cancel mid-pipeline from an external trigger. |
| **No timeout per stage** | `CompletableFuture` has `orTimeout()` but it cancels the whole chain — you can't set per-stage timeouts with independent retries. |
| **Scale ceiling** | Runs on Spring's `@Async` thread pool. Long-running OCR/AI tasks consume threads and starve other requests. |
| **No heartbeats** | If Tesseract hangs for 10 minutes processing a scanned page, nothing detects it — the thread is just stuck. |

> **Bottom line:** CompletableFuture is fire-and-forget. It works for simple async tasks but falls apart for multi-stage, long-running pipelines where reliability matters.

---

## The Solution — Temporal Workflow

📄 **`DocumentProcessingWorkflow.java`**

```java
@WorkflowInterface
public interface DocumentProcessingWorkflow {

    @WorkflowMethod
    ProcessingResult processDocument(DocumentRequest request);

    @QueryMethod
    PipelineStatus getStatus();

    @SignalMethod
    void requestCancellation(String reason);
}
```

| Annotation | Purpose |
|-----------|---------|
| **`@WorkflowMethod`** | Entry point. Accepts `DocumentRequest`, returns `ProcessingResult`. |
| **`@QueryMethod`** | Read-only live status query. No DB needed — returns in-memory state. |
| **`@SignalMethod`** | Async message for graceful mid-pipeline cancellation from UI. |

### Activity Stubs — Each Stage Gets Independent Timeouts & Retries

| Activity | Timeout | Retries | Heartbeat |
|----------|---------|---------|-----------|
| `UploadActivity` | 2 min | 3× | — |
| `TextExtractionActivity` | 5 min | 3× | 30s |
| `OcrActivity` | 10 min | 3× | 30s |
| `StorageActivity` | 3 min | 5× | — |
| `AiAnalyticsActivity` | 5 min | 3× | 30s |

📄 **`DocumentProcessingWorkflowImpl.java`**

```java
@WorkflowImpl(taskQueues = "DOCUMENT_PROCESSING_TASK_QUEUE")
public class DocumentProcessingWorkflowImpl implements DocumentProcessingWorkflow {

    private PipelineStatus status = PipelineStatus.STARTED;
    private boolean cancelRequested = false;

    private final UploadActivity uploadActivity = Workflow.newActivityStub(
        UploadActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
            .build());

    private final OcrActivity ocrActivity = Workflow.newActivityStub(
        OcrActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(10))
            .setHeartbeatTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
            .build());

    private final AiAnalyticsActivity aiActivity = Workflow.newActivityStub(
        AiAnalyticsActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .setDoNotRetry(TokenLimitExceededException.class.getName())
                .build())
            .build());

    // ... (TextExtractionActivity, StorageActivity stubs similar)

    @Override
    public ProcessingResult processDocument(DocumentRequest request) {

        // Stage 1 — Upload & Stage
        status = PipelineStatus.UPLOADING;
        StagedFile staged = uploadActivity.uploadAndStage(request);

        if (cancelRequested) return ProcessingResult.cancelled("After upload");

        // Stage 2 — Convert to Text
        status = PipelineStatus.EXTRACTING_TEXT;
        ExtractionResult extracted = extractActivity.convertToText(staged);

        // Stage 3 — Extract Images from PDF
        status = PipelineStatus.EXTRACTING_IMAGES;
        ImageExtractionResult images = extractActivity.extractImagesFromPdf(staged);

        if (cancelRequested) return ProcessingResult.cancelled("After extraction");

        // Stage 4 — OCR on extracted images
        status = PipelineStatus.RUNNING_OCR;
        OcrResult ocrResult = ocrActivity.runOcr(images);

        // Stage 5 — Merge text and persist
        status = PipelineStatus.STORING;
        String fullText = extracted.getText() + "\n" + ocrResult.getText();
        StorageResult stored = storageActivity.storeInDbAndElastic(
            request.getDocumentId(), fullText, extracted.getMetadata());

        if (cancelRequested) return ProcessingResult.cancelled("After storage");

        // Stage 6 — AI Analytics
        status = PipelineStatus.AI_ANALYTICS;
        AiResult aiResult = aiActivity.runAnalytics(fullText, request.getAnalyticsConfig());

        status = PipelineStatus.COMPLETED;
        return ProcessingResult.success(stored, aiResult);
    }

    @Override
    public PipelineStatus getStatus() { return status; }

    @Override
    public void requestCancellation(String reason) { cancelRequested = true; }
}
```

---

## Activity Definitions — Each Stage Isolated

Each pipeline stage becomes its own Activity with independent timeouts, retries, and heartbeats.

### Text Extraction Activity (with Heartbeats)

```java
@ActivityInterface
public interface TextExtractionActivity {
    ExtractionResult convertToText(StagedFile staged);
    ImageExtractionResult extractImagesFromPdf(StagedFile staged);
}

@Component
@ActivityImpl(taskQueues = "DOCUMENT_PROCESSING_TASK_QUEUE")
public class TextExtractionActivityImpl implements TextExtractionActivity {

    @Override
    public ExtractionResult convertToText(StagedFile staged) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();
        ctx.heartbeat("Starting text extraction");

        String text;
        switch (staged.getMimeType()) {
            case "application/pdf"  -> text = pdfBoxExtractor.extract(staged.getPath());
            case "application/docx" -> text = poiExtractor.extractDocx(staged.getPath());
            case "application/xlsx" -> text = poiExtractor.extractXlsx(staged.getPath());
            default                 -> text = tikaExtractor.extract(staged.getPath());
        }

        ctx.heartbeat("Text extraction complete");
        return new ExtractionResult(text, staged.getMetadata());
    }

    @Override
    public ImageExtractionResult extractImagesFromPdf(StagedFile staged) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();
        List<byte[]> images = new ArrayList<>();

        try (PDDocument doc = PDDocument.load(staged.getPath().toFile())) {
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                ctx.heartbeat("Extracting images from page " + (i + 1));
                images.addAll(imageExtractor.extractFromPage(doc.getPage(i)));
            }
        }
        return new ImageExtractionResult(images);
    }
}
```

### OCR Activity (Long-Running with Heartbeats)

```java
@Component
@ActivityImpl(taskQueues = "DOCUMENT_PROCESSING_TASK_QUEUE")
public class OcrActivityImpl implements OcrActivity {

    @Override
    public OcrResult runOcr(ImageExtractionResult images) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();
        StringBuilder allText = new StringBuilder();

        for (int i = 0; i < images.getImages().size(); i++) {
            ctx.heartbeat("OCR processing image " + (i + 1) + "/" + images.getImages().size());
            String text = tesseract.doOCR(images.getImages().get(i));
            allText.append(text).append("\n");
        }
        return new OcrResult(allText.toString());
    }
}
```

### AI Analytics Activity

```java
@Component
@ActivityImpl(taskQueues = "DOCUMENT_PROCESSING_TASK_QUEUE")
public class AiAnalyticsActivityImpl implements AiAnalyticsActivity {

    @Override
    public AiResult runAnalytics(String fullText, AnalyticsConfig config) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();

        ctx.heartbeat("Running NLP summarization");
        String summary = nlpService.summarize(fullText);

        ctx.heartbeat("Extracting entities");
        List<Entity> entities = nlpService.extractEntities(fullText);

        ctx.heartbeat("Classifying document");
        String classification = nlpService.classify(fullText, config.getCategories());

        ctx.heartbeat("Running sentiment analysis");
        SentimentScore sentiment = nlpService.analyzeSentiment(fullText);

        return new AiResult(summary, entities, classification, sentiment);
    }
}
```

> Every Activity calls `ctx.heartbeat()` so Temporal knows the process is alive. If Tesseract hangs or the AI model times out, Temporal detects the missing heartbeat and retries the Activity — not the entire pipeline.

📖 [Java SDK → Activity Heartbeats](https://docs.temporal.io/develop/java/failure-detection#activity-heartbeats)

---

## Heartbeats in Activities

Long-running stages call `ctx.heartbeat()` so Temporal detects hangs and retries automatically.

```java
// OcrActivityImpl.java
public OcrResult runOcr(ImageExtractionResult images) {
    ActivityExecutionContext ctx = Activity.getExecutionContext();

    for (int i = 0; i < images.getImages().size(); i++) {
        ctx.heartbeat("OCR image " + (i + 1));    // ← tells Temporal we're alive
        text += tesseract.doOCR(images.getImages().get(i));
    }
    return new OcrResult(text);
}
```

| Scenario | What Happens |
|----------|-------------|
| **Tesseract hangs on a scanned page** | Heartbeat missed → Temporal retries just this Activity |
| **`heartbeatTimeout: 30s`** | If no heartbeat in 30s, the attempt is considered failed |
| **Completed stages** | Upload and text extraction are NOT re-run — results are cached in event history |
| **AI model timeout / rate limit** | Heartbeat detects it → retry with exponential backoff |

📖 [Temporal Docs → Activity Heartbeats](https://docs.temporal.io/encyclopedia/detecting-activity-failures#activity-heartbeat)

---

## Caching Data Between Activities

*Activity return values are automatically persisted in the Event History — they become a durable cache for free*

### How It Works

```
┌──────────────────┐    ┌─────────────────────────┐    ┌─────────────┐    ┌──────────────────┐
│  UploadActivity   │    │ TextExtractionActivity   │    │ OcrActivity  │    │ StorageActivity   │
│                   │    │                          │    │              │    │                   │
│  → StagedFile     │───►│  → ExtractionResult      │───►│  → OcrResult │───►│  → StorageResult  │
│    filePath       │    │    rawText, metadata     │    │    ocrText   │    │    docId, indexed │
│    mimeType       │    │    pageCount             │    │              │    │                   │
└──────────────────┘    └─────────────────────────┘    └─────────────┘    └──────────────────┘
         │                         │                         │                       │
         ▼                         ▼                         ▼                       ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│  📦 Temporal Event History                                                                   │
│  ActivityCompleted → StagedFile  |  ActivityCompleted → ExtractionResult  |  ActivityCompleted│
│  → OcrResult  |  ActivityCompleted → StorageResult  |  ...                                   │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### The Three Rules of Inter-Activity Caching

**💾 Implicit cache** — Every Activity return value is serialized and stored in the Event History. The next Activity receives it as a normal function argument. No Redis, no temp database tables, no shared filesystem required.

**🔄 Replay-safe** — On crash recovery, Temporal replays the history. Completed Activities return their cached result instantly — the upload isn't re-uploaded, text isn't re-extracted, OCR isn't re-run. Only the Activity that was in-flight at the time of the crash is re-executed.

**⚠️ Size matters** — Event History stores the full serialized payload. For large binary data (images, PDFs), pass a *file reference* (path or S3 URL) rather than the raw bytes. Keep Activity payloads under 2 MB. The total Event History has a soft limit around 50 MB.

---

## Caching Patterns in Practice

*How the Workflow passes upload data and extracted text between Activities*

📄 **`DocumentProcessingWorkflowImpl.java`**

```java
// Stage 1 → returns StagedFile (cached in Event History)
StagedFile staged = uploadActivity.uploadAndStage(request);

// Stage 2 → uses staged (from cache), returns text (cached)
ExtractionResult extracted = extractActivity.convertToText(staged);

// Stage 3 → uses staged again for images (same cached value)
ImageExtractionResult images = extractActivity.extractImagesFromPdf(staged);

// Stage 4 → uses images (from cache), returns OCR text (cached)
OcrResult ocr = ocrActivity.runOcr(images);

// Merge — all data available in Workflow scope from cached returns
String fullText = extracted.getText() + "\n" + ocr.getText();

// Stage 5 → passes merged fullText to storage
storageActivity.storeInDbAndElastic(docId, fullText, extracted.getMetadata());

// Stage 6 → passes merged fullText to AI analytics
AiResult ai = aiActivity.runAnalytics(fullText, config);
```

### Pattern Breakdown

**Pass by value** — Each Activity returns a serializable object (POJO). The Workflow stores it in a local variable and passes it to the next Activity as a normal method argument. The Workflow itself is the "glue" that chains stages together.

**File references, not bytes** — `StagedFile` contains a file path or S3 URL — not the 50 MB PDF itself. Both `convertToText()` and `extractImagesFromPdf()` use that path to read the original file from shared storage.

```java
// Good — lightweight reference in Event History (~200 bytes)
public record StagedFile(Path filePath, String mimeType, DocumentMetadata metadata) {}

// Bad — raw file bytes in Event History (~50 MB)
public record StagedFile(byte[] fileContent, String mimeType) {}  // DON'T DO THIS
```

**Text accumulation** — `extracted.getText()` (from PDFBox/POI) and `ocr.getText()` (from Tesseract) are merged in the Workflow into `fullText`. This combined string is then passed to both `StorageActivity` and `AiAnalyticsActivity` — each receives the same merged text without either needing to know about the other.

**Replay = instant cache** — If the Worker crashes after Stage 3:

```
Replay starts →
  Stage 1: UploadActivity    → returns cached StagedFile (instant, no upload)
  Stage 2: TextExtraction    → returns cached ExtractionResult (instant, no parsing)
  Stage 3: ImageExtraction   → returns cached ImageExtractionResult (instant, no PDFBox)
  Stage 4: OcrActivity       → EXECUTES (this is where we left off)
  Stage 5: StorageActivity   → EXECUTES
  Stage 6: AiAnalytics       → EXECUTES
```

Stages 1–3 return their results from the Event History cache in milliseconds. The Worker resumes actual execution from Stage 4 — the first Activity that hadn't completed before the crash.

> **Activity return values are the cache.** No Redis, no temp DB tables, no shared state — just workflow variables backed by Temporal's durable Event History.

---

## Side-by-Side — CompletableFuture vs. Temporal

| Capability | CompletableFuture | Temporal |
|-----------|-------------------|----------|
| **Durability on restart** | ✗ All progress lost — entire chain re-runs | ✓ Replays event history, resumes from last completed Activity |
| **Per-stage retry** | ✗ `exceptionally()` catches all — no granular retry | ✓ Each Activity has its own `RetryOptions` with backoff |
| **Per-stage timeout** | ✗ `orTimeout()` kills the whole chain | ✓ `startToCloseTimeout` per Activity — OCR gets 10 min, upload gets 2 min |
| **Heartbeats (hang detection)** | ✗ Stuck thread goes unnoticed | ✓ `heartbeatTimeout` detects hung Tesseract or AI calls within seconds |
| **Inter-activity caching** | ✗ All in-memory — lost on crash | ✓ Activity return values persisted in Event History automatically |
| **Live status query** | ✗ Requires custom DB-backed status tracking | ✓ `@QueryMethod getStatus()` — zero additional infrastructure |
| **Graceful cancellation** | ✗ `cancel()` on a CompletableFuture is best-effort | ✓ `@SignalMethod requestCancellation()` checked between stages |
| **Observability** | ✗ Logs only — no structured event history | ✓ Full event history in Temporal Web UI (`localhost:8233`) |
| **Horizontal scaling** | ✗ Bound to Spring `@Async` thread pool on one JVM | ✓ Run more Worker instances — Temporal distributes tasks automatically |
| **Non-retryable errors** | ✗ Manual `instanceof` checks in `exceptionally()` | ✓ `setDoNotRetry(TokenLimitExceededException.class)` in `RetryOptions` |
| **Cross-language** | ✗ Java only | ✓ Python or Java Workers can execute the same Workflow |

---

## The Spring Boot Controller — Before and After

### Before (CompletableFuture)

```java
@PostMapping("/api/documents/upload")
public ResponseEntity<String> upload(@RequestParam MultipartFile file) {
    CompletableFuture<ProcessingResult> future = processingService.processDocument(file);
    // No workflow ID, no status tracking, no way to reconnect after restart
    return ResponseEntity.ok("Processing started");
}
```

### After (Temporal)

```java
@PostMapping("/api/worker/upload")
public ResponseEntity<Map<String, String>> upload(@RequestParam MultipartFile file) {
    String workflowId = "doc-" + UUID.randomUUID();

    WorkflowOptions opts = WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue("DOCUMENT_PROCESSING_TASK_QUEUE")
        .build();

    DocumentProcessingWorkflow wf = workflowClient.newWorkflowStub(
        DocumentProcessingWorkflow.class, opts);

    WorkflowClient.start(wf::processDocument, new DocumentRequest(file, workflowId));

    return ResponseEntity.accepted()
        .body(Map.of("workflowId", workflowId, "status", "ACCEPTED"));
}

@GetMapping("/api/worker/status/{workflowId}")
public ResponseEntity<PipelineStatus> getStatus(@PathVariable String workflowId) {
    DocumentProcessingWorkflow wf = workflowClient.newWorkflowStub(
        DocumentProcessingWorkflow.class, workflowId);
    return ResponseEntity.ok(wf.getStatus());
}

@PostMapping("/api/worker/cancel/{workflowId}")
public ResponseEntity<Void> cancel(@PathVariable String workflowId, @RequestBody String reason) {
    DocumentProcessingWorkflow wf = workflowClient.newWorkflowStub(
        DocumentProcessingWorkflow.class, workflowId);
    wf.requestCancellation(reason);
    return ResponseEntity.accepted().build();
}
```

| Endpoint | Method | What It Does |
|----------|--------|-------------|
| `/api/worker/upload` | POST | Start workflow → 202 Accepted + workflowId |
| `/api/worker/status/{id}` | GET | `@QueryMethod` → live pipeline stage, no DB |
| `/api/worker/cancel/{id}` | POST | `@SignalMethod` → graceful mid-pipeline cancel |

> The controller returns `202 Accepted` with a `workflowId`. The frontend polls `/status/{workflowId}` every 2 seconds. The `@QueryMethod` returns the live pipeline stage — no database, no Redis, no custom status table.

---

## What Happens When Things Fail

### Scenario 1 — Tesseract OCR crashes on page 47 of a 200-page PDF

| CompletableFuture | Temporal |
|-------------------|----------|
| Entire pipeline fails. 30 minutes of text extraction is lost. User must re-upload. | OCR Activity fails. Temporal retries just the OCR Activity (up to 3 times). Stages 1–3 are already completed and cached — they don't re-run. |

### Scenario 2 — Elasticsearch cluster goes down during indexing

| CompletableFuture | Temporal |
|-------------------|----------|
| `exceptionally()` logs the error. All OCR and extraction work is gone. | Storage Activity retries with exponential backoff (`setMaximumAttempts(5)`). Upload, extraction, and OCR results are safe in Temporal's event history. When ES comes back, the Activity succeeds. |

### Scenario 3 — Spring Boot server restarts mid-processing

| CompletableFuture | Temporal |
|-------------------|----------|
| All in-flight `CompletableFuture` chains vanish. No record of what was processing. | Worker restarts and re-registers. Temporal replays the event history — completed Activities return cached results, the pipeline resumes from the exact stage where it left off. |

### Scenario 4 — AI model API rate-limited during analytics

| CompletableFuture | Temporal |
|-------------------|----------|
| Pipeline fails or blocks a thread indefinitely. | AI Activity has `heartbeatTimeout(30s)` — if the API hangs, Temporal detects it. `RetryOptions` with `setBackoffCoefficient(2.0)` adds increasing delays between retries, naturally backing off from the rate limit. |

### Scenario 5 — User cancels a document that's mid-OCR

| CompletableFuture | Temporal |
|-------------------|----------|
| No mechanism. `future.cancel()` is unreliable for chains. | Frontend calls `POST /api/worker/cancel/{id}`. The `@SignalMethod` sets `cancelRequested = true`. The Workflow checks this between stages and returns a `CANCELLED` result cleanly. |

---

## Document Pipeline — Architecture Diagram

```
  ┌──────────────────────────┐
  │   Spring Boot REST API   │
  │                          │
  │  POST /api/worker/upload │──── WorkflowClient.start(wf::processDocument, request)
  │  GET  /api/worker/status │──── wf.getStatus()  (@QueryMethod)
  │  POST /api/worker/cancel │──── wf.requestCancellation()  (@SignalMethod)
  └────────────┬─────────────┘
               │ gRPC
               ▼
  ┌──────────────────────────────────────────┐
  │           Temporal Server                │
  │                                          │
  │   Event History (durable, replayable)    │
  │   Task Queue: DOCUMENT_PROCESSING_TASK_QUEUE │
  │   Web UI: http://localhost:8233          │
  └────────────┬─────────────────────────────┘
               │ gRPC (poll)
               ▼
  ┌──────────────────────────────────────────────────────────┐
  │                   Temporal Worker(s)                      │
  │                                                          │
  │  ┌─────────────────────────────────────────────────┐     │
  │  │  DocumentProcessingWorkflowImpl                 │     │
  │  │                                                 │     │
  │  │  Stage 1 → UploadActivity           (2 min, 3x) │     │
  │  │  Stage 2 → TextExtractionActivity   (5 min, 3x) │     │
  │  │  Stage 3 → ImageExtractionActivity  (5 min, 3x) │     │
  │  │  Stage 4 → OcrActivity             (10 min, 3x) │     │
  │  │  Stage 5 → StorageActivity          (3 min, 5x) │     │
  │  │  Stage 6 → AiAnalyticsActivity      (5 min, 3x) │     │
  │  └─────────────────────────────────────────────────┘     │
  │                                                          │
  │  Scale: Run N instances → Temporal distributes tasks     │
  └──────────────────────────────────────────────────────────┘
               │                    │
               ▼                    ▼
  ┌────────────────────┐  ┌────────────────────┐
  │   PostgreSQL /     │  │   Elasticsearch    │
  │   MySQL Database   │  │   Cluster          │
  └────────────────────┘  └────────────────────┘
```

---

## Worker Registration — Spring Boot Integration

📄 **`DocumentWorkerRegistrar.java`**

```java
@Component
public class DocumentWorkerRegistrar {

    @Autowired private WorkflowClient workflowClient;
    @Autowired private UploadActivityImpl uploadActivity;
    @Autowired private TextExtractionActivityImpl extractActivity;
    @Autowired private OcrActivityImpl ocrActivity;
    @Autowired private StorageActivityImpl storageActivity;
    @Autowired private AiAnalyticsActivityImpl aiActivity;

    @EventListener(ApplicationReadyEvent.class)
    public void startWorker() {
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);

        Worker worker = factory.newWorker(
            "DOCUMENT_PROCESSING_TASK_QUEUE",
            WorkerOptions.newBuilder()
                .setMaxConcurrentActivityExecutionSize(20)
                .setMaxConcurrentWorkflowTaskExecutionSize(10)
                .build());

        worker.registerWorkflowImplementationTypes(
            DocumentProcessingWorkflowImpl.class);

        worker.registerActivitiesImplementations(
            uploadActivity, extractActivity, ocrActivity,
            storageActivity, aiActivity);

        factory.start();
        log.info("Temporal Worker started on DOCUMENT_PROCESSING_TASK_QUEUE");
    }
}
```

> Because Activity implementations are Spring `@Component` beans, they can `@Autowired` repositories, Elasticsearch clients, AI services, and any other Spring-managed dependency — Temporal doesn't interfere with dependency injection.

📖 [Java SDK → Core Application](https://docs.temporal.io/develop/java/core-application)

---

## Configuration

📄 **`application.properties`**

```properties
# ── Temporal Connection ─────────────────────────────────────
# LOCAL DEV:
spring.temporal.connection.target=127.0.0.1:7233
spring.temporal.namespace=default

# Enable @WorkflowImpl / @ActivityImpl auto-discovery
spring.temporal.workers-auto-discovery.packages=com.example.temporal

# TEMPORAL CLOUD (production):
# spring.temporal.connection.target=<ns>.tmprl.cloud:7233
# spring.temporal.namespace=<your-namespace>
# spring.temporal.connection.mtls.key-file=/path/client.key
# spring.temporal.connection.mtls.cert-chain-file=/path/client.crt

# ── Database ────────────────────────────────────────────────
spring.datasource.url=jdbc:postgresql://localhost:5432/docprocessing
spring.jpa.hibernate.ddl-auto=update

# ── Elasticsearch ───────────────────────────────────────────
spring.elasticsearch.uris=http://localhost:9200
```

---

## Scaling as Clients Multiply

*What happens when 10, 100, or 1,000 clients submit documents simultaneously?*

### The Core Scaling Principle

Temporal fundamentally separates **submission** from **execution**. Clients submit workflow requests to the Temporal Server. Workers pull tasks from a Task Queue. These two sides scale independently — adding 100 more clients doesn't require adding 100 more Workers.

```
                          SUBMISSION SIDE                    EXECUTION SIDE
                        (scales with demand)              (scales with capacity)

  Client 1 ──┐                                        ┌── Worker 1
  Client 2 ──┤                                        ├── Worker 2
  Client 3 ──┤                                        ├── Worker 3
  ...        ├──►  Temporal Server  ──►  Task Queue  ──┼── Worker 4
  Client 98 ──┤      (manages state)     (durable)    ├── ...
  Client 99 ──┤      (queues tasks)      (buffered)   ├── Worker N-1
  Client 100──┘                                        └── Worker N
```

### Without Temporal — Single JVM Bottleneck

```
  Client 1 ──┐
  Client 2 ──┤──►  Spring Boot @Async Thread Pool  ──►  🔥 BOTTLENECK
  Client 3 ──┘      8 threads = 8 docs max
                     OCR hogs threads for 10+ minutes
                     Long AI jobs starve upload threads
                     Crash = all progress lost
                     No way to add capacity without redesign
```

The `@Async` thread pool is the ceiling. Long-running OCR and AI Activities consume threads, starving short upload requests. When the JVM restarts, every in-flight `CompletableFuture` vanishes. Vertical scaling (bigger JVM, more threads) only delays the problem.

### With Temporal — Elastic Workers

```
  Client 1 ──┐                                        ┌── Worker 1  (upload, text)
  Client 2 ──┤                                        ├── Worker 2  (upload, text)
  Client 3 ──┤──►  Temporal Server  ──►  Task Queue  ──┼── Worker 3  (OCR, GPU)
  Client 4 ──┤      (manages state)     (decoupled)   ├── Worker 4  (OCR, GPU)
  Client 5 ──┘                                        └── Worker 5  (AI, Python)
```

Temporal **decouples submission from execution**. Clients submit workflows to the server. Workers pull tasks from the queue independently. Adding more clients just means more workflows in the queue — Workers scale separately to match the load.

### Comparison: Scaling Dimensions

| Dimension | Without Temporal | With Temporal |
|-----------|-----------------|---------------|
| **Client throughput** | Limited by JVM thread pool (8–64 threads) | Unlimited — server queues all requests |
| **Worker throughput** | Same JVM as REST API — shared resources | Independent processes — scale to N |
| **Crash recovery** | All in-flight work lost | Replay from Event History, resume from last Activity |
| **Resource isolation** | OCR blocks upload threads | Separate Workers / queues for heavy Activities |
| **Adding capacity** | Vertical only (bigger JVM) | `kubectl scale deployment doc-worker --replicas=20` |
| **Backpressure** | None — excess requests rejected or queued in-memory | Task Queue buffers naturally — Workers drain at their own pace |
| **Cost efficiency** | Over-provision for peak or drop requests | Scale Workers up for peak, down for quiet periods |
| **Multi-region** | Application-level complexity | Temporal Cloud supports multi-region namespaces |

### How Backpressure Works

When clients submit faster than Workers can process, the Task Queue acts as a durable buffer:

```
  High traffic period:

  100 clients ──► Temporal Server ──► Task Queue (500 pending tasks)
                                            │
                                    Workers drain at ~50/min
                                    No tasks lost, no timeouts
                                    Auto-scale Workers to catch up

  Low traffic period:

  5 clients ──► Temporal Server ──► Task Queue (0–3 pending tasks)
                                            │
                                    Scale Workers down to save cost
                                    Idle Workers have zero overhead
```

Unlike an in-memory queue that drops tasks when full, the Temporal Task Queue is durable — tasks survive server restarts and are never lost. Workers process them at their own pace.

---

## Scaling Clients — Many Entry Points

*Any system that can make a gRPC call can be a Temporal Client*

| Client Type | Example | How It Starts Workflows |
|-------------|---------|------------------------|
| 🌐 **REST API** | Spring Boot controllers, FastAPI endpoints | User uploads via browser → `WorkflowClient.start()` |
| ⚡ **Microservices** | Internal order-processing, ingestion services | Service-to-service → Temporal Client SDK |
| ⌨️ **CLI Tools** | Java/Go CLI for batch uploads, admin ops | `java -jar client.jar start-async --steps 100` |
| 📅 **Scheduled Jobs** | Cron jobs, Temporal Schedules | Nightly batch → `ScheduleClient.create()` |
| 📨 **Event Consumers** | Kafka/SQS listeners | Message arrives → start workflow per message |
| 🤖 **AI Agents** | LLM orchestrators, LangChain pipelines | Agent decides to analyze a document programmatically |

```
  🌐 REST API ──────────┐
  ⚡ Microservices ──────┤
  ⌨️ CLI Tools ──────────┤
  📅 Scheduled Jobs ─────┼──►  Temporal Server  +  Task Queue
  📨 Event Consumers ────┤
  🤖 AI Agents ──────────┘
```

All clients converge on the **same Temporal Server** and **same Task Queue**. They don't need to know about each other — or about the Workers.

### Client Code Examples

**REST API Client (Spring Boot)**

```java
@PostMapping("/api/worker/upload")
public ResponseEntity<Map<String, String>> upload(@RequestParam MultipartFile file) {
    String workflowId = "doc-" + UUID.randomUUID();
    WorkflowClient.start(wf::processDocument, new DocumentRequest(file, workflowId));
    return ResponseEntity.accepted().body(Map.of("workflowId", workflowId));
}
```

**Event Consumer Client (Kafka)**

```java
@KafkaListener(topics = "document-ingestion")
public void onDocumentEvent(DocumentEvent event) {
    String workflowId = "doc-" + event.getDocumentId();
    WorkflowOptions opts = WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue("DOCUMENT_PROCESSING_TASK_QUEUE")
        .build();
    DocumentProcessingWorkflow wf = workflowClient.newWorkflowStub(
        DocumentProcessingWorkflow.class, opts);
    WorkflowClient.start(wf::processDocument, event.toRequest());
    log.info("Started workflow {} from Kafka event", workflowId);
}
```

**Scheduled Batch Client (Temporal Schedules)**

```java
// Create a schedule that runs every night at 2 AM
ScheduleClient scheduleClient = ScheduleClient.newInstance(workflowClient);
scheduleClient.createSchedule(
    "nightly-batch-reprocess",
    Schedule.newBuilder()
        .setAction(ScheduleActionStartWorkflow.newBuilder()
            .setWorkflowType("BatchReprocessWorkflow")
            .setTaskQueue("DOCUMENT_PROCESSING_TASK_QUEUE")
            .build())
        .setSpec(ScheduleSpec.newBuilder()
            .setCronExpressions(List.of("0 2 * * *"))  // 2 AM daily
            .build())
        .build());
```

**CLI Client (from the demo)**

```bash
# Start a workflow from the command line
java -jar temporal-longrunning-client.jar start-async \
    --job-id batch-2026-03-17 \
    --steps 50 \
    --task-queue DOCUMENT_PROCESSING_TASK_QUEUE

# Check status later
java -jar temporal-longrunning-client.jar status \
    --workflow-id long-running-batch-2026-03-17

# Get result when done
java -jar temporal-longrunning-client.jar result \
    --workflow-id long-running-batch-2026-03-17
```

**Python AI Agent Client**

```python
from temporalio.client import Client

async def ai_agent_triggers_analysis(document_url: str, analysis_type: str):
    client = await Client.connect("localhost:7233")

    result = await client.execute_workflow(
        "DocumentProcessingWorkflow",
        DocumentRequest(url=document_url, analytics_config=analysis_type),
        id=f"ai-agent-{uuid4()}",
        task_queue="DOCUMENT_PROCESSING_TASK_QUEUE",
    )
    return result  # AI agent uses this result for further reasoning
```

> **Client scaling is free:** adding more clients just means more workflows in the queue. Workers scale independently — add instances to handle the load. No client registration, no connection pooling between clients and Workers, no shared state.

---

## Production Scaling Patterns

*How to scale from 10 to 10,000+ concurrent document workflows*

### 1. Horizontal Worker Scaling

Run N Worker instances on the same Task Queue. Temporal distributes tasks round-robin. No leader election, no coordination code. Add and remove Workers at any time with zero downtime.

```bash
# Scale from 1 to 20 Workers instantly
kubectl scale deployment doc-worker --replicas=20

# Or use Horizontal Pod Autoscaler based on Task Queue depth
kubectl autoscale deployment doc-worker --min=2 --max=50 --cpu-percent=70
```

**How it works:** Every Worker instance polls the same `DOCUMENT_PROCESSING_TASK_QUEUE`. Temporal assigns each task to exactly one Worker. No sticky sessions, no partition keys, no manual shard assignment. A new Worker starts polling within seconds of startup.

```
  Task Queue: "DOCUMENT_PROCESSING_TASK_QUEUE"

  Before scaling:                   After scaling:
  ┌──────────┐                     ┌──────────┐
  │ Worker 1 │ ← 100% load         │ Worker 1 │ ← 20% load
  └──────────┘                     ├──────────┤
                                   │ Worker 2 │ ← 20% load
                                   ├──────────┤
                                   │ Worker 3 │ ← 20% load
                                   ├──────────┤
                                   │ Worker 4 │ ← 20% load
                                   ├──────────┤
                                   │ Worker 5 │ ← 20% load
                                   └──────────┘
```

### 2. Dedicated Task Queues

Route heavy Activities (OCR, AI) to specialized queues with GPU Workers. Lightweight Activities stay on standard Workers. A single Workflow can fan out to multiple queues.

```java
// In the Workflow — route OCR to GPU Workers
private final OcrActivity ocrActivity = Workflow.newActivityStub(
    OcrActivity.class,
    ActivityOptions.newBuilder()
        .setTaskQueue("gpu-ocr-queue")     // separate queue for GPU nodes
        .setStartToCloseTimeout(Duration.ofMinutes(10))
        .setHeartbeatTimeout(Duration.ofSeconds(30))
        .build());

// Route AI analytics to Python ML Workers
private final AiAnalyticsActivity aiActivity = Workflow.newActivityStub(
    AiAnalyticsActivity.class,
    ActivityOptions.newBuilder()
        .setTaskQueue("ai-analytics-queue")  // Python Workers with PyTorch
        .setStartToCloseTimeout(Duration.ofMinutes(5))
        .build());
```

```
  Workflow runs on:  "doc-pipeline"       →  Standard Workers (×10, Java)
  OCR Activity on:   "gpu-ocr-queue"      →  GPU Workers (×4, Java + Tesseract)
  AI Activity on:    "ai-analytics-queue"  →  ML Workers (×6, Python + PyTorch)
  Storage on:        "doc-pipeline"       →  Standard Workers (same pool)
```

This pattern lets you scale each Activity type independently based on its resource profile. OCR needs GPUs? Add GPU Workers. AI is the bottleneck? Scale up the Python ML pool. Upload is fast? Keep the standard pool small.

### 3. Worker Tuning

Control concurrency per Worker to match hardware. Prevent one long OCR job from blocking short uploads on the same Worker.

```java
Worker worker = factory.newWorker(
    "DOCUMENT_PROCESSING_TASK_QUEUE",
    WorkerOptions.newBuilder()
        .setMaxConcurrentActivityExecutionSize(20)       // 20 parallel activities
        .setMaxConcurrentWorkflowTaskExecutionSize(50)   // 50 parallel workflow tasks
        .setMaxConcurrentLocalActivityExecutionSize(10)  // 10 local activities
        .build());
```

**Tuning guidelines:**

| Worker Type | Activity Concurrency | Workflow Concurrency | Why |
|-------------|---------------------|---------------------|-----|
| **Standard** (upload, text, storage) | 20 | 50 | I/O-bound — more concurrency is fine |
| **GPU** (OCR) | 2–4 | 10 | GPU memory limits — fewer parallel jobs |
| **ML** (AI analytics) | 4–8 | 20 | Model inference is CPU/GPU-heavy |

### 4. Auto-Scaling with Kubernetes

Use Temporal's built-in metrics to drive Kubernetes Horizontal Pod Autoscaler (HPA). The key metric is **Task Queue backlog** — how many tasks are waiting.

```yaml
# Kubernetes HPA based on Temporal metrics via Prometheus
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: doc-worker-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: doc-worker
  minReplicas: 2
  maxReplicas: 50
  metrics:
    - type: Pods
      pods:
        metric:
          name: temporal_activity_schedule_to_start_latency
        target:
          type: AverageValue
          averageValue: "5000"   # scale up if tasks wait > 5 seconds
```

**Key Temporal metrics for auto-scaling:**

| Metric | What It Measures | Scale Up When |
|--------|-----------------|---------------|
| `temporal_activity_schedule_to_start_latency` | Time tasks wait in queue before a Worker picks them up | > 5 seconds (tasks backing up) |
| `temporal_sticky_cache_size` | Workflow cache entries per Worker | Approaching limit (cache evictions) |
| `temporal_worker_task_slots_available` | Free slots on each Worker | Approaching 0 (Worker saturated) |

📖 [Java SDK → Observability & Metrics](https://docs.temporal.io/develop/java/observability)

### 5. Multi-Namespace & Temporal Cloud

Isolate tenants or environments with namespaces. Temporal Cloud handles server scaling — you only manage Workers.

```properties
# Temporal Cloud — fully managed, auto-scaling server
spring.temporal.connection.target=<ns>.tmprl.cloud:7233
spring.temporal.namespace=prod-docs
spring.temporal.connection.mtls.key-file=/path/client.key
spring.temporal.connection.mtls.cert-chain-file=/path/client.crt
```

**Namespace isolation patterns:**

| Pattern | Namespaces | Use Case |
|---------|-----------|----------|
| **Per environment** | `dev-docs`, `staging-docs`, `prod-docs` | Standard SDLC isolation |
| **Per tenant** | `tenant-acme`, `tenant-globex` | Multi-tenant SaaS — each customer gets their own namespace |
| **Per team** | `team-ingest`, `team-analytics` | Large org — teams manage their own workflows independently |

> Temporal Server itself scales to millions of concurrent workflows. You only scale Workers — and Temporal distributes the work.

📖 [Temporal Cloud → Get Started](https://docs.temporal.io/cloud/get-started)

---

## Architecture at Scale

### High-Level Architecture

```
┌────────────────────────┐   ┌──────────────────────────┐   ┌─────────────────────────────┐
│  100+ Concurrent       │   │     1 Temporal Server     │   │     N Worker Instances       │
│  Clients               │   │                           │   │                              │
│                        │   │  Task Queues              │   │  Standard Workers (×10)      │
│  REST · CLI · Events   │──►│  Event History            │──►│  GPU Workers (×4, OCR + AI)  │──►  PostgreSQL
│  Schedules · Agents    │   │  Scheduler                │   │  ML Workers (×6, Python)     │──►  Elasticsearch
│  Microservices         │   │  Web UI :8233             │   │                              │──►  S3 / MinIO
└────────────────────────┘   └──────────────────────────┘   └─────────────────────────────┘
```

### Component Scaling Strategies

| Component | Scaling Strategy |
|-----------|-----------------|
| **Clients** | Add as many as needed — each just calls `WorkflowClient.start()`. No registration required. |
| **Temporal Server** | Self-managed: horizontal sharding across 4+ nodes. Cloud: fully managed by Temporal, auto-scales. |
| **Standard Workers** | `kubectl scale --replicas=N` — handles upload, text extraction, storage. I/O-bound, scale wide. |
| **GPU Workers** | Dedicated nodes with GPUs on `gpu-ocr-queue` — handles OCR and image processing. Scale by GPU count. |
| **ML Workers** | Python Workers with PyTorch on `ai-analytics-queue` — handles NLP tasks. Scale by model throughput. |
| **PostgreSQL** | Read replicas for query load. Partitioning by document date for large tables. |
| **Elasticsearch** | Cluster auto-scales on index size. Separate hot/warm/cold tiers for document lifecycle. |
| **S3 / Object Storage** | Effectively infinite — store staged files and extracted images by reference. |

### Capacity Planning by Growth Stage

| Stage | Concurrent Docs | Workers | Task Queues | Infrastructure |
|-------|----------------|---------|-------------|---------------|
| **Dev / POC** | 1–10 | 1 Worker (local) | 1 queue | Local Temporal CLI, single JVM |
| **Small team** | 10–50 | 2–5 Workers | 1 queue | Docker Compose, single Temporal server |
| **Production** | 50–500 | 5–20 Workers | 2–3 queues | Kubernetes, Temporal cluster (3 nodes) |
| **High volume** | 500–5,000 | 20–100 Workers | 3–5 queues (standard, GPU, AI) | K8s with HPA, Temporal Cloud or 5+ node cluster |
| **Enterprise** | 5,000+ | 100+ Workers | 5+ queues, multiple namespaces | Temporal Cloud, multi-region, dedicated GPU pools |

### Scaling the Temporal Server Itself

For self-hosted deployments, the Temporal Server consists of four services that scale independently:

```
  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
  │  Frontend    │  │  History     │  │  Matching    │  │  Worker      │
  │  Service     │  │  Service     │  │  Service     │  │  Service     │
  │             │  │             │  │             │  │  (internal)  │
  │  Accepts    │  │  Manages    │  │  Dispatches  │  │  System      │
  │  gRPC calls │  │  event      │  │  tasks to    │  │  workflows   │
  │  from       │  │  history    │  │  Workers via │  │  (archival,  │
  │  clients    │  │  per        │  │  Task Queues │  │   cleanup)   │
  │             │  │  workflow   │  │             │  │             │
  │  ×2–4       │  │  ×4–8       │  │  ×2–4       │  │  ×1–2       │
  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
         │                │                │                │
         └────────────────┼────────────────┼────────────────┘
                          ▼
               ┌─────────────────┐
               │  Persistence    │
               │  (PostgreSQL,   │
               │   Cassandra,    │
               │   or MySQL)     │
               └─────────────────┘
```

**With Temporal Cloud**, you skip all of this — Temporal manages the server, persistence, and scaling. You deploy only Workers.

### Multi-Region Deployment

For global applications, Temporal Cloud supports multi-region namespaces:

```
  Region: US-East                          Region: EU-West
  ┌──────────────────┐                    ┌──────────────────┐
  │  US Clients      │                    │  EU Clients      │
  │  US Workers      │◄──── Temporal ────►│  EU Workers      │
  │  US PostgreSQL   │      Cloud         │  EU PostgreSQL   │
  │  US Elasticsearch│   (multi-region)   │  EU Elasticsearch│
  └──────────────────┘                    └──────────────────┘
```

Workers can run in any region. Temporal routes tasks to the nearest available Worker. Event history is replicated for durability. This gives you both low latency (local Workers) and disaster recovery (cross-region failover).

> **Key insight:** Adding clients doesn't slow anything down. Each workflow is independent with its own Event History. Scale Workers to match inbound rate. Temporal Cloud handles server-side scaling for you.

---

## Key Takeaways

⚙️ **Activities** hold all side effects — I/O, HTTP, DB. Decorated with `@activity.defn` (Python) or `@ActivityInterface` (Java).

🔁 **Workflows** are deterministic orchestrators. They schedule Activities and define retry logic.

👷 **Workers** poll a Task Queue and execute both Workflows and Activities. Scale by adding instances.

📡 **Clients** (Python or Java) only talk to the Temporal Server — never directly to Workers.

🛡️ **Fault tolerance** — Temporal replays event history after crashes, skipping completed steps. Your code runs to completion.

💾 **Inter-activity caching** — Activity return values are persisted in the Event History. On replay, completed Activities return cached results instantly — no re-execution, no Redis, no temp tables.

📄 **Document pipelines** — Upload, convert, OCR, store, and analyze documents with per-stage retries, heartbeats, and live status queries. CompletableFuture chains can't offer any of this.

🔍 **Observability for free** — `@QueryMethod` replaces custom status-tracking tables. Temporal Web UI shows the full event history of every document processed.

🤖 **AI-ready** — Long-running NLP and model calls get their own Activity with `heartbeatTimeout` and `doNotRetry` for non-transient errors like token limits.

💻 **Spring Boot native** — Activity impls are Spring `@Component` beans. Autowire repositories, ES clients, and AI services normally.

🌐 **Cross-language** — Java CLI can start workflows that Python Workers execute — via gRPC + protobuf.

🔀 **Polyglot Workers** — Python, Java, and Go Workers can poll the same Task Queue simultaneously. Use Python for AI, Go for throughput, Java for enterprise — in the same Workflow.

📈 **Scaling is decoupled** — 1,000 clients submit workflows. Workers scale independently. Dedicated Task Queues route OCR to GPU nodes and AI to ML Workers. Temporal Cloud handles server-side scaling.

---

## References

| Resource | Link |
|----------|------|
| Python SDK Developer Guide | [docs.temporal.io/develop/python](https://docs.temporal.io/develop/python) |
| Java SDK Developer Guide | [docs.temporal.io/develop/java](https://docs.temporal.io/develop/java) |
| Go SDK Developer Guide | [docs.temporal.io/develop/go](https://docs.temporal.io/develop/go) |
| Python — Core Application | [docs.temporal.io/develop/python/core-application](https://docs.temporal.io/develop/python/core-application) |
| Java — Core Application | [docs.temporal.io/develop/java/core-application](https://docs.temporal.io/develop/java/core-application) |
| Go — Core Application | [docs.temporal.io/develop/go/core-application](https://docs.temporal.io/develop/go/core-application) |
| Python — Temporal Client | [docs.temporal.io/develop/python/temporal-client](https://docs.temporal.io/develop/python/temporal-client) |
| Java — Temporal Client | [docs.temporal.io/develop/java/temporal-client](https://docs.temporal.io/develop/java/temporal-client) |
| Go — Temporal Client | [docs.temporal.io/develop/go/temporal-client](https://docs.temporal.io/develop/go/temporal-client) |
| Python — Failure Detection | [docs.temporal.io/develop/python/failure-detection](https://docs.temporal.io/develop/python/failure-detection) |
| Java — Failure Detection | [docs.temporal.io/develop/java/failure-detection](https://docs.temporal.io/develop/java/failure-detection) |
| Go — Failure Detection | [docs.temporal.io/develop/go/failure-detection](https://docs.temporal.io/develop/go/failure-detection) |
| Java — Message Passing (Query/Signal) | [docs.temporal.io/develop/java/message-passing](https://docs.temporal.io/develop/java/message-passing) |
| Java — Observability & Logging | [docs.temporal.io/develop/java/observability](https://docs.temporal.io/develop/java/observability) |
| Python — Sandbox | [docs.temporal.io/develop/python/python-sdk-sandbox](https://docs.temporal.io/develop/python/python-sdk-sandbox) |
| Task Queues & Naming | [docs.temporal.io/task-queue/naming](https://docs.temporal.io/task-queue/naming) |
| Activity Heartbeats | [docs.temporal.io/encyclopedia/detecting-activity-failures#activity-heartbeat](https://docs.temporal.io/encyclopedia/detecting-activity-failures#activity-heartbeat) |
| Temporal Cloud Setup | [docs.temporal.io/cloud/get-started](https://docs.temporal.io/cloud/get-started) |
| Java SDK on GitHub | [github.com/temporalio/sdk-java](https://github.com/temporalio/sdk-java) |
| Go SDK on GitHub | [github.com/temporalio/sdk-go](https://github.com/temporalio/sdk-go) |
| Python SDK on PyPI | [pypi.org/project/temporalio](https://pypi.org/project/temporalio/) |
| Java SDK on Maven Central | [search.maven.org — temporal-sdk](https://search.maven.org/artifact/io.temporal/temporal-sdk) |
| Workers — Concepts | [docs.temporal.io/workers](https://docs.temporal.io/workers) |
| Task Queues — Concepts | [docs.temporal.io/task-queue](https://docs.temporal.io/task-queue) |
| Temporal Cloud — Pricing & Scaling | [docs.temporal.io/cloud](https://docs.temporal.io/cloud) |
| Worker Performance Tuning | [docs.temporal.io/develop/worker-performance](https://docs.temporal.io/develop/worker-performance) |
| Temporal Visibility (Search Attributes) | [docs.temporal.io/visibility](https://docs.temporal.io/visibility) |