package com.docupload.controller;

import com.docupload.temporal.stub.DocumentWorkflowClientStub;
import com.docupload.temporal.workflow.DocumentWorkflowRequest;
import com.docupload.temporal.workflow.DocumentWorkflowResult;
import com.docupload.temporal.workflow.WorkflowStatusSnapshot;
import io.temporal.api.common.v1.WorkflowExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller that accepts document uploads and drives them through a
 * <strong>Temporal workflow pipeline</strong> instead of the inline
 * {@code CompletableFuture} approach used by {@link DocumentController}.
 *
 * <h3>Key difference from DocumentController</h3>
 * <ul>
 *   <li>{@link DocumentController} runs everything in-process on a Spring
 *       {@code @Async} thread pool — if the server restarts the job is lost.</li>
 *   <li>{@link UploadWorkerController} submits work to Temporal, which persists
 *       the workflow's event history and retries failed activities automatically.
 *       The server can restart at any time; Temporal resumes from the last
 *       completed activity.</li>
 * </ul>
 *
 * <h3>Flow</h3>
 * <pre>
 *   POST /api/worker/upload
 *        │
 *        ├─ (1) Stage file bytes to object storage         [TODO: implement]
 *        ├─ (2) Build DocumentWorkflowRequest (storageKey)
 *        ├─ (3) documentWorkflowClientStub.startWorkflow() → Temporal
 *        └─ (4) Return 202 Accepted  { workflowId, jobId, statusUrl }
 *
 *   GET /api/worker/status/{workflowId}
 *        │
 *        └─ Temporal @QueryMethod → WorkflowStatusSnapshot (live, non-blocking)
 *
 *   GET /api/worker/result/{workflowId}
 *        │
 *        └─ awaitResult() → blocks until workflow completes → DocumentWorkflowResult
 *
 *   DELETE /api/worker/cancel/{workflowId}
 *        │
 *        └─ Temporal @SignalMethod → requestCancellation(reason)
 * </pre>
 *
 * <h3>Object storage</h3>
 * Before calling Temporal the controller should stage the file bytes to S3/MinIO
 * and pass only the storage key to the workflow (Temporal history has a 2 MB
 * per-event size limit).  This is stubbed with a placeholder storageKey for now.
 */
@RestController
@RequestMapping("/api/worker")
@CrossOrigin(origins = "*")
public class UploadWorkerController {

    private static final Logger log = LoggerFactory.getLogger(UploadWorkerController.class);

    private final DocumentWorkflowClientStub workflowClient;

    public UploadWorkerController(DocumentWorkflowClientStub workflowClient) {
        this.workflowClient = workflowClient;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/worker/upload  — submit a single document to the Temporal pipeline
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Accept an uploaded file, stage it (placeholder), and submit a Temporal workflow.
     *
     * <p>Returns {@code 202 Accepted} immediately — the document is processed
     * asynchronously.  The response body contains the {@code workflowId} that the
     * client uses to poll for status.</p>
     *
     * @param file  the uploaded document
     * @return      202 with workflowId and a status polling URL
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadToWorker(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No file was provided."));
        }

        // ── (1) Generate a stable job ID ──────────────────────────────────────
        String jobId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // ── (2) Stage file to object storage ─────────────────────────────────
        // TODO: Upload file.getBytes() to S3/MinIO and obtain a real storage key.
        // For now we use a placeholder — the Activity implementation will need
        // a real key to retrieve the bytes.
        String storageKey = "uploads/" + Instant.now().toEpochMilli() + "/" + jobId + "/original";
        log.info("[STUB] Would stage {} bytes to storageKey={}", file.getSize(), storageKey);

        // ── (3) Build the workflow request ────────────────────────────────────
        DocumentWorkflowRequest request = new DocumentWorkflowRequest(
                jobId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                storageKey,
                Instant.now().toString()
        );

        // ── (4) Start the Temporal workflow (non-blocking) ────────────────────
        WorkflowExecution execution = workflowClient.startWorkflow(request);
        log.info("Temporal workflow started workflowId={} runId={}", execution.getWorkflowId(), execution.getRunId());

        // ── (5) Return 202 with polling coordinates ───────────────────────────
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId",       jobId);
        body.put("workflowId",  execution.getWorkflowId());
        body.put("runId",       execution.getRunId());
        body.put("statusUrl",   "/api/worker/status/"  + execution.getWorkflowId());
        body.put("resultUrl",   "/api/worker/result/"  + execution.getWorkflowId());
        body.put("cancelUrl",   "/api/worker/cancel/"  + execution.getWorkflowId());
        body.put("submittedAt", Instant.now().toString());

        return ResponseEntity.accepted().body(body);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/worker/status/{workflowId}  — live pipeline status (poll this)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Query the workflow's current pipeline stage via Temporal's
     * {@code @QueryMethod}.  This is strongly consistent and non-blocking —
     * the answer comes directly from the worker's in-memory state.
     *
     * <p>The HTML frontend calls this endpoint on a short interval (e.g. every
     * 1–2 s) to drive its progress UI.</p>
     *
     * @param workflowId  the workflow / job ID returned by {@code /upload}
     * @return            a {@link WorkflowStatusSnapshot} with stage, progress %, message
     */
    @GetMapping("/status/{workflowId}")
    public ResponseEntity<WorkflowStatusSnapshot> getStatus(
            @PathVariable String workflowId) {

        WorkflowStatusSnapshot snapshot = workflowClient.queryStatus(workflowId);
        return ResponseEntity.ok(snapshot);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/worker/result/{workflowId}  — await the final result (blocking)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Block until the named workflow completes and return its
     * {@link DocumentWorkflowResult}.
     *
     * <p>The controller returns a {@code CompletableFuture} so Spring MVC does
     * not block a platform thread while waiting — it suspends the request and
     * resumes when the future resolves.</p>
     *
     * <p>Clients that prefer polling should use {@code /status} and only call
     * this endpoint once the status reaches {@code COMPLETED}.</p>
     *
     * @param workflowId  the workflow / job ID
     * @return            the final extraction result
     */
    @GetMapping("/result/{workflowId}")
    public CompletableFuture<ResponseEntity<DocumentWorkflowResult>> getResult(
            @PathVariable String workflowId) {

        return workflowClient.awaitResult(workflowId)
                .thenApply(result -> {
                    if ("FAILED".equals(result.getStatus()) || "CANCELLED".equals(result.getStatus())) {
                        return ResponseEntity.internalServerError().body(result);
                    }
                    return ResponseEntity.ok(result);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/worker/cancel/{workflowId}  — cancel a running workflow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Send a cancellation signal to a running workflow.
     *
     * @param workflowId  the workflow to cancel
     * @param reason      optional human-readable reason (query param)
     * @return            204 No Content on success
     */
    @DeleteMapping("/cancel/{workflowId}")
    public ResponseEntity<Void> cancelWorkflow(
            @PathVariable String workflowId,
            @RequestParam(required = false, defaultValue = "User requested cancellation") String reason) {

        workflowClient.cancelWorkflow(workflowId, reason);
        log.info("Cancellation signal sent workflowId={} reason={}", workflowId, reason);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/worker/health
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status",   "UP",
                "service",  "UploadWorker API (Temporal)",
                "taskQueue", "DOCUMENT_PROCESSING_TASK_QUEUE",
                "version",  "1.0.0"
        ));
    }
}
