package demo.temporal.client;

import demo.temporal.model.FileProcessingRequest;
import demo.temporal.model.FileProcessingResult;
import demo.temporal.shared.TaskQueues;
import demo.temporal.workflow.FileProcessingWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Client library for submitting file-processing Workflows to Temporal.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * try (FileProcessorClient client = FileProcessorClient.builder()
 *         .temporalAddress("localhost:7233")
 *         .namespace("default")
 *         .build()) {
 *
 *     FileProcessingResult result = client.processFileSync(
 *             "report.pdf", "/data/inbox/report.pdf", "/data/outbox", null);
 *
 *     System.out.println("Output: " + result.getTextOutputPath());
 * }
 * }</pre>
 */
public class FileProcessorClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FileProcessorClient.class);

    private final WorkflowServiceStubs serviceStubs;
    private final WorkflowClient workflowClient;

    private FileProcessorClient(WorkflowServiceStubs stubs, WorkflowClient client) {
        this.serviceStubs = stubs;
        this.workflowClient = client;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Synchronous API
    // ═════════════════════════════════════════════════════════════════
    /**
     * Submit a file for processing and <b>block</b> until the result is
     * available.
     *
     * @return the completed result
     */
    public FileProcessingResult processFileSync(
            String fileName,
            String fileLocation,
            String outputLocation,
            Map<String, String> metadata) {

        FileProcessingRequest request = new FileProcessingRequest(
                fileName, fileLocation, outputLocation, metadata);

        FileProcessingWorkflow workflow = createWorkflowStub(request);
        log.info("Starting synchronous workflow for: {}", fileName);

        return workflow.processFile(request);
    }

    // ═════════════════════════════════════════════════════════════════
    //  Asynchronous API
    // ═════════════════════════════════════════════════════════════════
    /**
     * Submit a file for processing <b>asynchronously</b>. Returns immediately
     * with a Future and the Workflow ID.
     */
    public AsyncHandle processFileAsync(
            String fileName,
            String fileLocation,
            String outputLocation,
            Map<String, String> metadata) {

        FileProcessingRequest request = new FileProcessingRequest(
                fileName, fileLocation, outputLocation, metadata);

        FileProcessingWorkflow workflow = createWorkflowStub(request);
        String workflowId = TaskQueues.WORKFLOW_ID_PREFIX
                + fileName.replaceAll("[^a-zA-Z0-9._-]", "_") + "-"
                + UUID.randomUUID().toString().substring(0, 8);

        WorkflowClient.start(workflow::processFile, request);
        log.info("Started async workflow {} for: {}", workflowId, fileName);

        // Create an untyped stub to fetch the result later
        WorkflowStub untypedStub = workflowClient.newUntypedWorkflowStub(workflowId);

        return new AsyncHandle(
                workflowId,
                CompletableFuture.supplyAsync(
                        () -> untypedStub.getResult(FileProcessingResult.class))
        );
    }

    // ═════════════════════════════════════════════════════════════════
    //  Query & Signal
    // ═════════════════════════════════════════════════════════════════
    /**
     * Query the current status of a running Workflow.
     */
    public String queryStatus(String workflowId) {
        FileProcessingWorkflow stub = workflowClient.newWorkflowStub(
                FileProcessingWorkflow.class, workflowId);
        return stub.getStatus();
    }

    /**
     * Signal a running Workflow to cancel processing.
     */
    public void cancelProcessing(String workflowId) {
        FileProcessingWorkflow stub = workflowClient.newWorkflowStub(
                FileProcessingWorkflow.class, workflowId);
        stub.cancelProcessing();
        log.info("Sent cancel signal to workflow: {}", workflowId);
    }

    // ═════════════════════════════════════════════════════════════════
    //  Internal helpers
    // ═════════════════════════════════════════════════════════════════
    private FileProcessingWorkflow createWorkflowStub(FileProcessingRequest request) {
        String workflowId = TaskQueues.WORKFLOW_ID_PREFIX
                + request.getFileName().replaceAll("[^a-zA-Z0-9._-]", "_") + "-"
                + UUID.randomUUID().toString().substring(0, 8);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TaskQueues.FILE_PROCESSING_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofHours(1))
                .build();

        return workflowClient.newWorkflowStub(FileProcessingWorkflow.class, options);
    }

    @Override
    public void close() {
        serviceStubs.shutdown();
        log.info("Client connection closed.");
    }

    // ═════════════════════════════════════════════════════════════════
    //  Async handle
    // ═════════════════════════════════════════════════════════════════
    /**
     * Handle returned by {@link #processFileAsync}.
     */
    public static final class AsyncHandle {

        private final String workflowId;
        private final CompletableFuture<FileProcessingResult> future;

        public AsyncHandle(String workflowId,
                CompletableFuture<FileProcessingResult> future) {
            this.workflowId = workflowId;
            this.future = future;
        }

        public String getWorkflowId() {
            return workflowId;
        }

        public CompletableFuture<FileProcessingResult> getFuture() {
            return future;
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Builder
    // ═════════════════════════════════════════════════════════════════
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String temporalAddress = "localhost:7233";
        private String namespace = "default";

        public Builder temporalAddress(String v) {
            this.temporalAddress = v;
            return this;
        }

        public Builder namespace(String v) {
            this.namespace = v;
            return this;
        }

        public FileProcessorClient build() {
            WorkflowServiceStubs stubs = WorkflowServiceStubs.newServiceStubs(
                    WorkflowServiceStubsOptions.newBuilder()
                            .setTarget(temporalAddress)
                            .build());

            WorkflowClient client = WorkflowClient.newInstance(stubs,
                    WorkflowClientOptions.newBuilder()
                            .setNamespace(namespace)
                            .build());

            return new FileProcessorClient(stubs, client);
        }
    }
}
