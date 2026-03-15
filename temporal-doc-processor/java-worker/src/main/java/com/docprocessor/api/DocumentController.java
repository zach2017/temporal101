package com.docprocessor.api;

import com.docprocessor.model.DocumentResult;
import com.docprocessor.worker.TemporalConfig;
import com.docprocessor.workflow.DocumentDownloadWorkflow;
import com.docprocessor.workflow.PdfProcessingWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final WorkflowClient workflowClient;

    @Value("${storage.path}")
    private String storagePath;

    public DocumentController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    /**
     * POST /api/upload
     * Upload a PDF file → triggers the PdfProcessingWorkflow
     * Returns the workflow result with the extracted text file path.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadPdf(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("error", "No file provided");
            return ResponseEntity.badRequest().body(response);
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !originalFileName.toLowerCase().endsWith(".pdf")) {
            response.put("error", "Only PDF files are accepted");
            return ResponseEntity.badRequest().body(response);
        }

        String documentId = UUID.randomUUID().toString();
        log.info("Received upload: {} → documentId: {}", originalFileName, documentId);

        try {
            // Save uploaded file to storage/uploads/{documentId}/
            Path uploadDir = Paths.get(storagePath, "uploads", documentId);
            Files.createDirectories(uploadDir);
            Path uploadPath = uploadDir.resolve(originalFileName);
            file.transferTo(uploadPath.toFile());
            log.info("PDF saved to: {}", uploadPath);

            // Start the Temporal workflow
            PdfProcessingWorkflow workflow = workflowClient.newWorkflowStub(
                    PdfProcessingWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId("pdf-process-" + documentId)
                            .setTaskQueue(TemporalConfig.TASK_QUEUE)
                            .build()
            );

            // Execute synchronously (blocks until workflow completes)
            DocumentResult result = workflow.processPdf(documentId, originalFileName);

            response.put("documentId", result.getDocumentId());
            response.put("originalFileName", result.getOriginalFileName());
            response.put("textFilePath", result.getTextFilePath());
            response.put("status", result.getStatus());
            response.put("extractedCharCount", result.getExtractedCharCount());
            response.put("workflowId", "pdf-process-" + documentId);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("File upload failed", e);
            response.put("error", "File upload failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (Exception e) {
            log.error("Workflow execution failed", e);
            response.put("error", "Processing failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/download/{documentId}
     * Downloads extracted text via the DocumentDownloadWorkflow.
     */
    @GetMapping("/download/{documentId}")
    public ResponseEntity<Map<String, Object>> downloadDocument(@PathVariable String documentId) {
        Map<String, Object> response = new HashMap<>();
        log.info("Download request for document: {}", documentId);

        try {
            DocumentDownloadWorkflow workflow = workflowClient.newWorkflowStub(
                    DocumentDownloadWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId("download-" + documentId + "-" + System.currentTimeMillis())
                            .setTaskQueue(TemporalConfig.TASK_QUEUE)
                            .build()
            );

            String content = workflow.downloadDocument(documentId);

            response.put("documentId", documentId);
            response.put("content", content);
            response.put("charCount", content.length());
            response.put("status", "SUCCESS");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Download workflow failed for document: {}", documentId, e);
            response.put("error", "Download failed: " + e.getMessage());
            response.put("documentId", documentId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    /**
     * GET /api/health
     * Simple health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "doc-processor");
        return ResponseEntity.ok(response);
    }
}
