# DocPulse — Document Upload Processor

Spring Boot 3.5 application that accepts document uploads, processes them
asynchronously via `CompletableFuture`, and streams the extracted text back
to a Tailwind CSS / vanilla-JS frontend.

---

## Stack

| Layer      | Technology                                    |
|------------|-----------------------------------------------|
| Backend    | Spring Boot **3.5.0**, Java 21                |
| Async      | `@Async` + `CompletableFuture` (thread pool)  |
| Upload     | `MultipartFile` · 50 MB single / 100 MB batch |
| Frontend   | Static HTML · Tailwind CDN · Vanilla JS       |
| Fonts      | Syne (display) · Space Mono (code)            |

---

## Project Structure

```
docupload/
├── pom.xml
└── src/main/
    ├── java/com/docupload/
    │   ├── DocUploadApplication.java      # @SpringBootApplication + @EnableAsync
    │   ├── config/
    │   │   └── AppConfig.java             # ThreadPoolTaskExecutor (4–16 threads)
    │   ├── controller/
    │   │   └── DocumentController.java    # POST /api/documents/upload  (single)
    │   │                                  # POST /api/documents/upload/batch
    │   │                                  # GET  /api/documents/health
    │   ├── model/
    │   │   └── DocumentProcessingResult.java
    │   └── service/
    │       └── DocumentProcessingService.java  # @Async CompletableFuture processing
    └── resources/
        ├── application.properties
        └── static/
            └── index.html                 # Tailwind + JS frontend
```

---

## How the Async Flow Works

```
Browser POST /api/documents/upload
        │
        ▼
DocumentController.uploadDocument()      ← Servlet thread freed immediately
        │   returns CompletableFuture<ResponseEntity>
        ▼
DocumentProcessingService.processDocument()   ← runs on doc-processor-* thread
        │
        ├── Stage 1: Security Scan        sleep(1_500 ms)
        ├── Stage 2: OCR / Parsing        sleep(2_500 ms)
        └── Stage 3: NLP Extraction       sleep(1_500 ms)
                                          ──────────────
                                          Total ≈ 5.5 s
        │
        ▼
CompletableFuture.completedFuture(result)
        │
        ▼
Spring serialises ResponseEntity<DocumentProcessingResult> → JSON → Browser
```

For **batch** uploads the controller fires one `CompletableFuture` per file
(all run in parallel) and then calls `CompletableFuture.allOf(...).get()`.

---

## Running

### Prerequisites
- Java 21+
- Maven 3.9+

### Start

```bash
cd docupload
mvn spring-boot:run
```

Then open **http://localhost:8080**

### Build fat-jar

```bash
mvn clean package
java -jar target/docupload-1.0.0.jar
```

---

## API Reference

### `POST /api/documents/upload`
Upload a single document.

| Field | Type            | Description       |
|-------|-----------------|-------------------|
| file  | multipart/form  | Document to parse |

**Response** `200 OK`
```json
{
  "jobId": "A3F9C1B2",
  "originalFileName": "report.pdf",
  "fileSizeBytes": 204800,
  "contentType": "application/pdf",
  "extractedText": "...",
  "processingTimeMs": 5520,
  "status": "SUCCESS"
}
```

### `POST /api/documents/upload/batch`
Upload multiple files (processed in parallel).

| Field | Type                  | Description         |
|-------|-----------------------|---------------------|
| files | multipart/form (list) | Documents to parse  |

**Response** `200 OK` — array of result objects (same schema as above).

### `GET /api/documents/health`
```json
{ "status": "UP", "service": "Document Processing API", "version": "1.0.0" }
```
