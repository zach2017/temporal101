package com.docupload.controller;

import com.docupload.model.DocumentProcessingResult;
import com.docupload.service.DocumentProcessingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class DocumentController {

    private final DocumentProcessingService processingService;

    public DocumentController(DocumentProcessingService processingService) {
        this.processingService = processingService;
    }

    /**
     * Single document upload — uses CompletableFuture for async processing.
     * The thread is freed while the document is being processed.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<DocumentProcessingResult>> uploadDocument(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            DocumentProcessingResult error = new DocumentProcessingResult(
                    "N/A", "N/A", "N/A", 0, "N/A",
                    "No file was provided.", 0, "ERROR"
            );
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body(error));
        }

        return processingService.processDocument(file)
                .thenApply(result -> {
                    if ("ERROR".equals(result.getStatus())) {
                        return ResponseEntity.internalServerError().body(result);
                    }
                    return ResponseEntity.ok(result);
                });
    }

    /**
     * Batch upload — launches all documents in parallel via CompletableFuture,
     * then waits for all of them with allOf() before returning.
     */
    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadBatch(
            @RequestParam("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No files provided"));
        }

        List<CompletableFuture<DocumentProcessingResult>> futures = files.stream()
                .filter(f -> !f.isEmpty())
                .map(processingService::processDocument)
                .toList();

        CompletableFuture<Void> allDone = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allDone.get(); // block until all complete
            List<DocumentProcessingResult> results = futures.stream()
                    .map(f -> {
                        try { return f.get(); }
                        catch (InterruptedException | ExecutionException e) {
                            Thread.currentThread().interrupt();
                            return new DocumentProcessingResult(
                                    "ERR", "N/A", "N/A", 0, "N/A",
                                    "Failed: " + e.getMessage(), 0, "ERROR");
                        }
                    })
                    .toList();
            return ResponseEntity.ok(results);
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Batch processing failed: " + e.getMessage()));
        }
    }

    /** Health / info endpoint */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Document Processing API",
                "version", "1.0.0"
        ));
    }
}
