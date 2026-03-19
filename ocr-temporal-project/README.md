# Tesseract OCR Temporal Worker

A Java-based Temporal Worker that performs Optical Character Recognition (OCR) using the Tesseract engine via Tess4J. The system converts images containing text into machine-readable strings, supporting multiple file source locations, multiple languages, and robust error handling.

## Project Structure

```
ocr-temporal-project/
├── pom.xml                          # Parent POM
├── Dockerfile                       # Docker deployment
├── ocr-common/                      # Shared library (for clients & worker)
│   ├── pom.xml
│   └── src/main/java/.../common/
│       ├── model/                   # OcrRequest, OcrResult, PreprocessingOptions
│       ├── enums/                   # OcrEngineMode, PageSegmentationMode, etc.
│       ├── workflow/                # OcrWorkflow interface
│       ├── activity/                # OcrActivities interface
│       ├── exception/               # Custom exceptions
│       └── constants/               # OcrConstants
└── ocr-worker/                      # Temporal worker implementation
    ├── pom.xml
    └── src/main/java/.../worker/
        ├── OcrWorkerApplication.java  # Bootstrap main class
        ├── workflow/                  # OcrWorkflowImpl
        ├── activity/                  # OcrActivitiesImpl
        ├── service/                   # TesseractOcrService, ImagePreprocessor
        └── resolver/                  # FileResolver, S3/Local/URL resolvers
```

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.9+
- Tesseract OCR installed (`apt install tesseract-ocr`)
- Temporal server running (`temporal server start-dev`)
- Language data files in tessdata directory

### Build

```bash
mvn clean package
```

### Run the Worker

```bash
export TEMPORAL_ADDRESS=localhost:7233
export TESSDATA_PATH=/usr/share/tesseract-ocr/5/tessdata
java -jar ocr-worker/target/ocr-worker-1.0.0.jar
```

### Client Usage

Add the shared library dependency:

```xml
<dependency>
    <groupId>com.example.ocr</groupId>
    <artifactId>ocr-common</artifactId>
    <version>1.0.0</version>
</dependency>
```

Submit a workflow:

```java
WorkflowClient client = WorkflowClient.newInstance(
    WorkflowServiceStubs.newLocalServiceStubs());

OcrWorkflow workflow = client.newWorkflowStub(OcrWorkflow.class,
    WorkflowOptions.newBuilder()
        .setTaskQueue("OCR_TASK_QUEUE")
        .setWorkflowExecutionTimeout(Duration.ofMinutes(10))
        .build());

OcrRequest request = OcrRequest.builder()
    .fileName("invoice.png")
    .fileLocation("s3://my-bucket/invoices/invoice.png")
    .language("eng")
    .build();

OcrResult result = workflow.processImage(request);

if (result.isTextFound()) {
    System.out.println("Extracted text: " + result.getText());
    System.out.println("Confidence: " + result.getMeanConfidence() + "%");
} else {
    System.out.println("No text found. Warnings: " + result.getWarnings());
}
```

## File Sources

| Source | Format | Example |
|--------|--------|---------|
| S3 | `s3://bucket/key` | `s3://my-bucket/scans/page1.png` |
| Local | Absolute path | `/mnt/shared/documents/scan.tiff` |
| URL | `http(s)://...` | `https://example.com/images/receipt.jpg` |

## Language Support

Specify languages using ISO 639-3 codes. Multiple languages with `+`:

```java
.language("eng")           // English only
.language("eng+deu+fra")   // English + German + French
.language("chi_sim")       // Simplified Chinese
```

## Configuration

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `TEMPORAL_ADDRESS` | `localhost:7233` | Temporal server address |
| `TESSDATA_PATH` | `/usr/share/tessdata` | Tessdata directory |
| `OCR_TEMP_DIR` | `/tmp/ocr-worker` | Temp file directory |
| `AWS_REGION` | `us-east-1` | AWS region for S3 |
| `OCR_MIN_CONFIDENCE` | `15.0` | Min confidence threshold |
| `OCR_TASK_QUEUE` | `OCR_TASK_QUEUE` | Temporal task queue |

## Docker

```bash
mvn clean package
docker build -t ocr-worker .
docker run -e TEMPORAL_ADDRESS=host.docker.internal:7233 ocr-worker
```

Install additional languages by adding to the Dockerfile:

```dockerfile
RUN apt-get install -y tesseract-ocr-jpn tesseract-ocr-kor
```
