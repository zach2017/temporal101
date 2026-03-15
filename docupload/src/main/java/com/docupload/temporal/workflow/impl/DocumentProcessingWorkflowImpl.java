package com.docupload.temporal.workflow.impl;

import com.docupload.temporal.activity.NlpAnalysisActivity;
import com.docupload.temporal.activity.SecurityScanActivity;
import com.docupload.temporal.activity.TextExtractionActivity;
import com.docupload.temporal.workflow.DocumentProcessingWorkflow;
import com.docupload.temporal.workflow.DocumentWorkflowRequest;
import com.docupload.temporal.workflow.DocumentWorkflowResult;
import com.docupload.temporal.workflow.WorkflowStatusSnapshot;
import com.docupload.temporal.workflow.WorkflowStatusSnapshot.PipelineStage;
import com.docupload.temporal.workflow.WorkflowStatusSnapshot.State;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;

/**
 * ════════════════════════════════════════════════════════════════
 *  Workflow Implementation — Document Processing Pipeline
 * ════════════════════════════════════════════════════════════════
 *
 * HOW TO WIRE TO TEMPORAL
 * ───────────────────────
 * 1. @WorkflowImpl(taskQueues = TASK_QUEUE) is picked up by the
 *    temporal-spring-boot-autoconfigure starter — no manual Worker
 *    registration needed when auto-discovery is enabled.
 *
 * 2. The starter creates a Worker on DOCUMENT_PROCESSING_TASK_QUEUE
 *    that registers both this workflow class AND the three activity
 *    impl beans automatically.
 *
 * 3. Required application.properties entries:
 *
 *      # Temporal server (local dev)
 *      spring.temporal.connection.target=127.0.0.1:7233
 *      spring.temporal.namespace=default
 *
 *      # Enable auto-discovery of @WorkflowImpl / @ActivityImpl beans
 *      spring.temporal.workers-auto-discovery.packages=com.docupload.temporal
 *
 * 4. Start the dev server before running the Spring Boot app:
 *      temporal server start-dev
 *
 * IMPORTANT TEMPORAL CONSTRAINTS
 * ───────────────────────────────
 * Workflow code MUST be deterministic — i.e. given the same event
 * history it always produces the same execution path.  Therefore:
 *
 *   ✗  NEVER call System.currentTimeMillis() directly in workflow code.
 *      Use Workflow.currentTimeMillis() instead.
 *   ✗  NEVER use java.util.Random, ThreadLocalRandom, UUID.randomUUID().
 *   ✗  NEVER call Thread.sleep(). Use Workflow.sleep() if needed.
 *   ✗  NEVER do I/O (DB, HTTP, files) in the workflow class itself.
 *      Put all I/O inside Activity implementations.
 *   ✓  Use Workflow.getLogger() — not SLF4J directly.
 *
 * ACTIVITY OPTIONS & RETRY POLICY
 * ─────────────────────────────────
 * Each activity stub is configured with:
 *   • scheduleToCloseTimeout — max total time including all retries
 *   • startToCloseTimeout    — max time for a single attempt
 *   • heartbeatTimeout       — if no heartbeat received in this window,
 *                              Temporal marks the activity as failed
 *   • RetryOptions           — 3 max attempts, exponential back-off
 *
 * @QueryMethod  (getStatus)
 * ─────────────────────────
 * Returns the current pipeline stage / progress to the poller
 * (UploadWorkerController.getStatus) in a strongly-consistent,
 * non-blocking read.  Fields are updated directly from workflow state
 * so the query never touches the database.
 *
 * @SignalMethod (requestCancellation)
 * ────────────────────────────────────
 * Sets a volatile flag that is checked between activity invocations.
 * When set, the workflow returns a CANCELLED result gracefully instead
 * of starting the next activity.
 */
@WorkflowImpl(taskQueues = "DOCUMENT_PROCESSING_TASK_QUEUE")
public class DocumentProcessingWorkflowImpl implements DocumentProcessingWorkflow {

    // Workflow-safe logger (replays correctly during event-history replay)
    private static final Logger log = Workflow.getLogger(DocumentProcessingWorkflowImpl.class);

    // ── Live state (mutable fields are safe here — Temporal serialises them) ─
    private WorkflowStatusSnapshot currentStatus = new WorkflowStatusSnapshot();
    private boolean cancellationRequested = false;

    // ── Activity stubs ────────────────────────────────────────────────────────
    // Stubs are proxies; calling a method on them schedules an activity task on
    // the Temporal server and blocks the workflow coroutine until it completes.

    private final SecurityScanActivity scanActivity = Workflow.newActivityStub(
            SecurityScanActivity.class,
            ActivityOptions.newBuilder()
                    .setScheduleToCloseTimeout(Duration.ofMinutes(3))
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setHeartbeatTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build()
    );

    private final TextExtractionActivity extractActivity = Workflow.newActivityStub(
            TextExtractionActivity.class,
            ActivityOptions.newBuilder()
                    .setScheduleToCloseTimeout(Duration.ofMinutes(5))
                    .setStartToCloseTimeout(Duration.ofMinutes(4))
                    .setHeartbeatTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(2))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build()
    );

    private final NlpAnalysisActivity nlpActivity = Workflow.newActivityStub(
            NlpAnalysisActivity.class,
            ActivityOptions.newBuilder()
                    .setScheduleToCloseTimeout(Duration.ofMinutes(3))
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setHeartbeatTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build()
    );

    // ═════════════════════════════════════════════════════════════════════════
    //  @WorkflowMethod — main pipeline orchestration
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public DocumentWorkflowResult processDocument(DocumentWorkflowRequest request) {
        String workflowId = Workflow.getInfo().getWorkflowId();
        long   workflowStartMs = Workflow.currentTimeMillis();

        log.info("Workflow started — workflowId={} jobId={} file={}",
                workflowId, request.getJobId(), request.getOriginalFileName());

        // Initialise status snapshot
        updateStatus(workflowId, request.getJobId(), State.QUEUED, PipelineStage.NOT_STARTED,
                5, "Workflow queued — waiting for worker…", workflowStartMs);

        DocumentWorkflowResult result = new DocumentWorkflowResult();
        result.setJobId(request.getJobId());
        result.setOriginalFileName(request.getOriginalFileName());
        result.setFileSizeBytes(request.getFileSizeBytes());
        result.setContentType(request.getContentType());

        // ── STAGE 1: Security Scan ────────────────────────────────────────────
        if (cancellationRequested) return cancelled(result, workflowStartMs);

        updateStatus(workflowId, request.getJobId(), State.RUNNING, PipelineStage.SECURITY_SCAN,
                15, "Stage 1/3 — Security Scan & Validation…", workflowStartMs);

        long scanStart = Workflow.currentTimeMillis();
        SecurityScanActivity.ScanResult scanResult = scanActivity.scan(request);
        result.setScanActivityMs(Workflow.currentTimeMillis() - scanStart);

        if (!scanResult.isClean()) {
            log.warn("Scan failed — verdict={} jobId={}", scanResult.getVerdict(), request.getJobId());
            result.setStatus("FAILED");
            result.setFailureReason("Security scan failed: " + scanResult.getVerdict());
            result.setExtractedText("Document rejected during security scan: " + scanResult.getVerdict());
            result.setTotalProcessingMs(Workflow.currentTimeMillis() - workflowStartMs);
            updateStatus(workflowId, request.getJobId(), State.FAILED, PipelineStage.SECURITY_SCAN,
                    100, "Failed: " + scanResult.getVerdict(), workflowStartMs);
            return result;
        }

        // ── STAGE 2: Text Extraction ──────────────────────────────────────────
        if (cancellationRequested) return cancelled(result, workflowStartMs);

        updateStatus(workflowId, request.getJobId(), State.RUNNING, PipelineStage.TEXT_EXTRACTION,
                45, "Stage 2/3 — OCR & Text Extraction…", workflowStartMs);

        long extractStart = Workflow.currentTimeMillis();
        TextExtractionActivity.ExtractionResult extractionResult =
                extractActivity.extract(request, scanResult);
        result.setExtractActivityMs(Workflow.currentTimeMillis() - extractStart);

        // ── STAGE 3: NLP Analysis ─────────────────────────────────────────────
        if (cancellationRequested) return cancelled(result, workflowStartMs);

        updateStatus(workflowId, request.getJobId(), State.RUNNING, PipelineStage.NLP_ANALYSIS,
                75, "Stage 3/3 — NLP Analysis & Indexing…", workflowStartMs);

        long nlpStart = Workflow.currentTimeMillis();
        NlpAnalysisActivity.AnalysisResult analysisResult =
                nlpActivity.analyze(request, extractionResult);
        result.setAnalyzeActivityMs(Workflow.currentTimeMillis() - nlpStart);

        // ── Assemble final result ─────────────────────────────────────────────
        long totalMs = Workflow.currentTimeMillis() - workflowStartMs;

        result.setExtractedText(buildFinalReport(request, scanResult, extractionResult, analysisResult, totalMs));
        result.setStatus("SUCCESS");
        result.setTotalProcessingMs(totalMs);

        updateStatus(workflowId, request.getJobId(), State.COMPLETED, PipelineStage.DONE,
                100, "All stages complete — results ready.", workflowStartMs);

        log.info("Workflow complete — workflowId={} jobId={} totalMs={}",
                workflowId, request.getJobId(), totalMs);
        return result;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  @QueryMethod — returns live status snapshot (non-blocking)
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public WorkflowStatusSnapshot getStatus() {
        return currentStatus;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  @SignalMethod — graceful cancellation
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void requestCancellation(String reason) {
        log.info("Cancellation signal received — reason={}", reason);
        cancellationRequested = true;
        currentStatus.setStatusMessage("Cancellation requested: " + reason);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void updateStatus(String workflowId, String jobId, State state, PipelineStage stage,
                               int pct, String message, long startMs) {
        currentStatus.setWorkflowId(workflowId);
        currentStatus.setJobId(jobId);
        currentStatus.setState(state);
        currentStatus.setCurrentStage(stage);
        currentStatus.setProgressPercent(pct);
        currentStatus.setStatusMessage(message);
        currentStatus.setElapsedMs(Workflow.currentTimeMillis() - startMs);
        currentStatus.setLastUpdatedAt(Instant.ofEpochMilli(Workflow.currentTimeMillis()).toString());
    }

    private DocumentWorkflowResult cancelled(DocumentWorkflowResult result, long startMs) {
        result.setStatus("CANCELLED");
        result.setFailureReason("Cancelled by user request");
        result.setExtractedText("Workflow was cancelled before completion.");
        result.setTotalProcessingMs(Workflow.currentTimeMillis() - startMs);
        currentStatus.setState(State.CANCELLED);
        currentStatus.setStatusMessage("Workflow cancelled.");
        return result;
    }

    /**
     * Assembles the human-readable final extraction report that is displayed
     * in the browser's result panel after all three stages complete.
     */
    private static String buildFinalReport(
            DocumentWorkflowRequest req,
            SecurityScanActivity.ScanResult scan,
            TextExtractionActivity.ExtractionResult extract,
            NlpAnalysisActivity.AnalysisResult nlp,
            long totalMs) {

        StringBuilder sb = new StringBuilder();
        sb.append("══════════════════════════════════════════════════════════════════\n");
        sb.append("  TEMPORAL PIPELINE EXTRACTION REPORT\n");
        sb.append("══════════════════════════════════════════════════════════════════\n\n");

        sb.append("PIPELINE METADATA\n");
        sb.append("──────────────────\n");
        sb.append(String.format("  Job ID          : %s%n", req.getJobId()));
        sb.append(String.format("  File            : %s%n", req.getOriginalFileName()));
        sb.append(String.format("  Size            : %s%n", formatBytes(req.getFileSizeBytes())));
        sb.append(String.format("  MIME (detected) : %s%n", scan.getMimeType()));
        sb.append(String.format("  Parser used     : %s%n", extract.getParserUsed()));
        sb.append(String.format("  Pages           : %d%n", extract.getPageCount()));
        sb.append(String.format("  Words           : %,d%n", extract.getWordCount()));
        sb.append(String.format("  Security scan   : %s  (%d ms)%n", scan.getVerdict(), scan.getDurationMs()));
        sb.append(String.format("  Extraction time : %d ms%n", extract.getDurationMs()));
        sb.append(String.format("  NLP time        : %d ms%n", nlp.getDurationMs()));
        sb.append(String.format("  Total pipeline  : %d ms%n", totalMs));
        sb.append(String.format("  Document type   : %s%n", nlp.getDocumentType()));
        sb.append(String.format("  Language        : %s  (confidence %.0f%%)%n",
                nlp.getLanguage(), nlp.getConfidenceScore() * 100));

        sb.append("\nAI SUMMARY\n");
        sb.append("───────────\n");
        sb.append("  ").append(nlp.getSummary()).append("\n");

        sb.append("\nKEY TOPICS\n");
        sb.append("───────────\n");
        nlp.getKeyTopics().forEach(t -> sb.append("  • ").append(t).append("\n"));

        sb.append("\nNAMED ENTITIES\n");
        sb.append("───────────────\n");
        nlp.getNamedEntities().forEach((entity, type) ->
                sb.append(String.format("  %-28s  %s%n", entity, type)));

        sb.append("\n══════════════════════════════════════════════════════════════════\n");
        sb.append("  EXTRACTED DOCUMENT CONTENT\n");
        sb.append("══════════════════════════════════════════════════════════════════\n\n");
        sb.append(extract.getRawText());
        sb.append("\n══════════════════════════════════════════════════════════════════\n");
        sb.append(String.format("  END — Processed in %d ms via Temporal Worker Pipeline%n", totalMs));
        sb.append("══════════════════════════════════════════════════════════════════\n");

        return sb.toString();
    }

    private static String formatBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024.0);
        return String.format("%.2f MB", b / (1024.0 * 1024));
    }
}
