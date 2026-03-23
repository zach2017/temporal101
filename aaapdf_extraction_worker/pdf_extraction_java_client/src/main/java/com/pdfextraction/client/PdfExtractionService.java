package com.pdfextraction.client;

import com.pdfextraction.config.TemporalConfig;
import com.pdfextraction.model.PdfExtractionRequest;
import com.pdfextraction.model.PdfExtractionResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Application service that encapsulates starting and awaiting
 * PDF extraction workflows.
 * <p>
 * Provides both synchronous (blocking) and asynchronous APIs so
 * callers can choose the concurrency model that fits their context.
 */
public final class PdfExtractionService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    private final WorkflowClient workflowClient;
    private final TemporalConfig config;

    public PdfExtractionService(TemporalConfig config) {
        this.config = config;
        this.workflowClient = TemporalClientFactory.create(config);
    }

    // ── Synchronous (blocking) ───────────────────────────────

    /**
     * Start a workflow and block until the result is available.
     */
    public PdfExtractionResult extractSync(PdfExtractionRequest request) {
        var stub = buildStub(request);

        log.info("Starting synchronous extraction for '{}'", request.fileName());
        PdfExtractionResult result = stub.extract(request.fileName(), request.fileLocation());
        log.info("Extraction complete: {}", result.documentName());

        return result;
    }

    // ── Asynchronous (non-blocking) ──────────────────────────

    /**
     * Start a workflow and return a {@link CompletableFuture} that
     * completes when the workflow finishes.
     */
    public CompletableFuture<PdfExtractionResult> extractAsync(PdfExtractionRequest request) {
        return CompletableFuture.supplyAsync(() -> extractSync(request));
    }

    // ── Fire-and-forget ──────────────────────────────────────

    /**
     * Start a workflow without waiting for the result.
     * Returns the workflow ID so callers can query status later.
     */
    public String extractFireAndForget(PdfExtractionRequest request) {
        String workflowId = generateWorkflowId(request);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(config.taskQueue())
                .setWorkflowExecutionTimeout(Duration.ofMinutes(30))
                .build();

        PdfExtractionWorkflowStub stub =
                workflowClient.newWorkflowStub(PdfExtractionWorkflowStub.class, options);

        WorkflowClient.start(stub::extract, request.fileName(), request.fileLocation());
        log.info("Workflow started (fire-and-forget): {}", workflowId);

        return workflowId;
    }

    // ── Query a running or completed workflow ────────────────

    /**
     * Retrieve the result of an already-started workflow by its ID.
     * Blocks until the workflow completes.
     */
    public PdfExtractionResult getResult(String workflowId) {
        PdfExtractionWorkflowStub stub =
                workflowClient.newWorkflowStub(PdfExtractionWorkflowStub.class, workflowId);

        return stub.extract(null, null);   // won't re-start; fetches existing result
    }

    // ── Lifecycle ────────────────────────────────────────────

    @Override
    public void close() {
        workflowClient.getWorkflowServiceStubs().shutdown();
        log.info("Temporal client shut down");
    }

    // ── Internal helpers ─────────────────────────────────────

    private PdfExtractionWorkflowStub buildStub(PdfExtractionRequest request) {
        String workflowId = generateWorkflowId(request);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(config.taskQueue())
                .setWorkflowExecutionTimeout(Duration.ofMinutes(30))
                .build();

        return workflowClient.newWorkflowStub(PdfExtractionWorkflowStub.class, options);
    }

    private static String generateWorkflowId(PdfExtractionRequest request) {
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        return "pdf-extract-%s-%s".formatted(request.fileName(), shortId);
    }
}
