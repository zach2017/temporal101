package com.docprocessing.client;

import com.docprocessing.config.TemporalConfig;
import com.docprocessing.model.DocumentProcessingRequest;
import com.docprocessing.model.DocumentProcessingResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Application service that publishes document-processing workflow
 * executions to the Temporal {@code document-intake-queue}.
 *
 * <p>The Python intake worker listens on that queue, picks up the
 * task, detects the MIME type, and routes it to the correct
 * extraction pipeline (PDF / image / office doc / etc.).
 *
 * <p>Provides three invocation styles:
 * <ul>
 *   <li>{@link #processSync}           — block until result</li>
 *   <li>{@link #processAsync}          — returns CompletableFuture</li>
 *   <li>{@link #processFireAndForget}  — returns workflow ID immediately</li>
 * </ul>
 */
public final class DocumentProcessingService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final WorkflowClient workflowClient;
    private final TemporalConfig config;

    public DocumentProcessingService(TemporalConfig config) {
        this.config = config;
        this.workflowClient = TemporalClientFactory.create(config);
    }

    // ── Synchronous (blocking) ───────────────────────────────

    /**
     * Publish a workflow to the intake queue and block until the
     * Python worker returns the result.
     */
    public DocumentProcessingResult processSync(DocumentProcessingRequest request) {
        var stub = buildStub(request);

        log.info("Publishing workflow for '{}' to queue '{}'",
                request.fileName(), config.taskQueue());

        DocumentProcessingResult result = stub.process(
                request.fileName(),
                request.fileLocation(),
                request.fileType()
        );

        log.info("Workflow complete: {} (category={})",
                result.documentName(), result.category());

        return result;
    }

    // ── Asynchronous (non-blocking) ──────────────────────────

    /**
     * Publish and return a {@link CompletableFuture} that completes
     * when the Python worker finishes processing.
     */
    public CompletableFuture<DocumentProcessingResult> processAsync(DocumentProcessingRequest request) {
        return CompletableFuture.supplyAsync(() -> processSync(request));
    }

    // ── Fire-and-forget ──────────────────────────────────────

    /**
     * Publish the workflow to the queue without waiting for the result.
     * Returns the workflow ID so you can query status later.
     */
    public String processFireAndForget(DocumentProcessingRequest request) {
        String workflowId = generateWorkflowId(request);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(config.taskQueue())
                .setWorkflowExecutionTimeout(Duration.ofMinutes(30))
                .build();

        DocumentIntakeWorkflowStub stub =
                workflowClient.newWorkflowStub(DocumentIntakeWorkflowStub.class, options);

        WorkflowClient.start(stub::process,
                request.fileName(),
                request.fileLocation(),
                request.fileType());

        log.info("Workflow published (fire-and-forget): id={} queue={}",
                workflowId, config.taskQueue());

        return workflowId;
    }

    // ── Query a running / completed workflow ──────────────────

    /**
     * Retrieve the result of an already-started workflow by ID.
     * Blocks until the workflow completes.
     */
    public DocumentProcessingResult getResult(String workflowId) {
        DocumentIntakeWorkflowStub stub =
                workflowClient.newWorkflowStub(DocumentIntakeWorkflowStub.class, workflowId);

        return stub.process(null, null, null);
    }

    // ── Lifecycle ────────────────────────────────────────────

    @Override
    public void close() {
        workflowClient.getWorkflowServiceStubs().shutdown();
        log.info("Temporal client shut down");
    }

    // ── Internal helpers ─────────────────────────────────────

    private DocumentIntakeWorkflowStub buildStub(DocumentProcessingRequest request) {
        String workflowId = generateWorkflowId(request);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(config.taskQueue())
                .setWorkflowExecutionTimeout(Duration.ofMinutes(30))
                .build();

        return workflowClient.newWorkflowStub(DocumentIntakeWorkflowStub.class, options);
    }

    private static String generateWorkflowId(DocumentProcessingRequest request) {
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        return "doc-intake-%s-%s".formatted(request.fileName(), shortId);
    }
}
