package com.docupload.temporal.stub;

import com.docupload.temporal.workflow.DocumentProcessingWorkflow;
import com.docupload.temporal.workflow.DocumentWorkflowRequest;
import com.docupload.temporal.workflow.DocumentWorkflowResult;
import com.docupload.temporal.workflow.WorkflowStatusSnapshot;
import com.docupload.temporal.worker.DocumentWorker;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Service façade that wraps Temporal's {@link WorkflowClient} and provides
 * the clean API consumed by {@link com.docupload.controller.UploadWorkerController}.
 *
 * <h3>Why a façade?</h3>
 * The controller should not depend directly on Temporal SDK types — this class
 * acts as an anti-corruption layer, translating between the REST domain model
 * and the Temporal workflow SDK.
 *
 * <h3>Workflow ID strategy</h3>
 * Each document job uses {@code jobId} as the Temporal workflow ID.  This gives
 * idempotency: re-uploading the same jobId is rejected with a meaningful error
 * rather than starting a duplicate run.
 *
 * <h3>Status polling</h3>
 * {@link #queryStatus(String)} uses Temporal's {@code @QueryMethod} mechanism —
 * a strongly-consistent read directly against the workflow's in-memory state.
 * This is more efficient than storing status in a side database.
 *
 * NOTE: The {@code WorkflowClient} bean is expected to be auto-configured by
 * {@code temporal-spring-boot-autoconfigure}.  When the Temporal server is not
 * running, this service will still load but calls to {@link #startWorkflow} will
 * throw a connection error — handle that in the controller.
 */
@Service
public class DocumentWorkflowClientStub {

    private static final Logger log = LoggerFactory.getLogger(DocumentWorkflowClientStub.class);

    /** Injected by temporal-spring-boot-autoconfigure when Temporal is available. */
    private final WorkflowClient workflowClient;

    public DocumentWorkflowClientStub(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    // ── Start ─────────────────────────────────────────────────────────────────

    /**
     * Submit a new document processing workflow to Temporal and return the
     * workflow/run IDs immediately (non-blocking).
     *
     * @param request  the document metadata
     * @return         the Temporal {@link WorkflowExecution} (workflowId + runId)
     * @throws WorkflowExecutionAlreadyStarted if {@code request.getJobId()} is re-used
     */
    public WorkflowExecution startWorkflow(DocumentWorkflowRequest request) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(DocumentWorker.TASK_QUEUE)
                .setWorkflowId(request.getJobId())
                // Workflow will time out after 10 minutes — prevents orphaned runs
                .setWorkflowExecutionTimeout(Duration.ofMinutes(10))
                // Each individual activity gets up to 2 minutes per attempt
                .setWorkflowRunTimeout(Duration.ofMinutes(10))
                .build();

        DocumentProcessingWorkflow workflow =
                workflowClient.newWorkflowStub(DocumentProcessingWorkflow.class, options);

        log.info("Starting Temporal workflow for jobId={}", request.getJobId());
        return WorkflowClient.start(workflow::processDocument, request);
    }

    // ── Query status (non-blocking) ───────────────────────────────────────────

    /**
     * Query the workflow's current pipeline stage and progress without blocking.
     * Uses Temporal's {@code @QueryMethod} — reads directly from the worker's
     * in-memory state; does NOT add an event to the history.
     *
     * @param workflowId  the jobId used when starting the workflow
     * @return            a live {@link WorkflowStatusSnapshot}
     */
    public WorkflowStatusSnapshot queryStatus(String workflowId) {
        DocumentProcessingWorkflow stub =
                workflowClient.newWorkflowStub(DocumentProcessingWorkflow.class, workflowId);
        return stub.getStatus();
    }

    // ── Await result (blocking, use on a virtual thread or @Async) ────────────

    /**
     * Block until the workflow completes and return its final result.
     *
     * <p>This is wrapped in a {@link CompletableFuture} so the Spring MVC layer
     * can return it without tying up a platform thread.  In practice the
     * controller calls this only when the client explicitly requests a blocking
     * wait (e.g. a CLI tool); the normal flow is to poll {@link #queryStatus}.</p>
     *
     * @param workflowId  the jobId / workflow ID
     * @return            {@link CompletableFuture} that resolves to the final result
     */
    public CompletableFuture<DocumentWorkflowResult> awaitResult(String workflowId) {
        return CompletableFuture.supplyAsync(() -> {
            WorkflowStub untyped = workflowClient.newUntypedWorkflowStub(workflowId);
            return untyped.getResult(DocumentWorkflowResult.class);
        });
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    /**
     * Send a cancellation signal to a running workflow.
     *
     * @param workflowId  the workflow to cancel
     * @param reason      human-readable reason recorded in the workflow history
     */
    public void cancelWorkflow(String workflowId, String reason) {
        log.info("Cancelling workflow workflowId={} reason={}", workflowId, reason);
        DocumentProcessingWorkflow stub =
                workflowClient.newWorkflowStub(DocumentProcessingWorkflow.class, workflowId);
        stub.requestCancellation(reason);
    }
}
