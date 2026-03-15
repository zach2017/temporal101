package com.docupload.temporal.worker;

/**
 * Marker interface for the Temporal Worker that hosts the document-processing
 * pipeline on the {@code DOCUMENT_PROCESSING_TASK_QUEUE} task queue.
 *
 * <h3>What a Temporal Worker does</h3>
 * A Worker is a long-running process that:
 * <ol>
 *   <li>Connects to the Temporal service (local dev-server or Temporal Cloud)</li>
 *   <li>Polls a named <em>task queue</em> for workflow and activity tasks</li>
 *   <li>Executes the matching Workflow or Activity implementation</li>
 *   <li>Reports the result back to Temporal, which advances the workflow's history</li>
 * </ol>
 *
 * <h3>Task Queue</h3>
 * All workflow and activity implementations in this module share a single task
 * queue named {@link #TASK_QUEUE}.  The {@code UploadWorkerController} passes
 * this name when starting a workflow via {@code WorkflowClient}.
 *
 * <h3>Implementation</h3>
 * The concrete class {@code DocumentWorkerRegistrar} (to be implemented) will:
 * <ul>
 *   <li>Obtain a {@code WorkerFactory} from the auto-configured {@code WorkflowClient}</li>
 *   <li>Create a {@code Worker} on {@link #TASK_QUEUE}</li>
 *   <li>Register {@code DocumentProcessingWorkflowImpl} as the workflow type</li>
 *   <li>Register {@code SecurityScanActivityImpl}, {@code TextExtractionActivityImpl},
 *       and {@code NlpAnalysisActivityImpl} as activity implementations</li>
 *   <li>Start the factory so workers begin polling</li>
 * </ul>
 *
 * @see DocumentWorkerRegistrar  (implementation — to be created)
 */
public interface DocumentWorker {

    /** The Temporal task queue name shared by the workflow and all its activities. */
    String TASK_QUEUE = "DOCUMENT_PROCESSING_TASK_QUEUE";

    /**
     * Start polling the task queue and begin accepting workflow/activity tasks.
     * Called once at application startup.
     */
    void start();

    /**
     * Gracefully shut down the worker, allowing in-flight tasks to complete.
     * Called on application shutdown (Spring {@code @PreDestroy}).
     */
    void shutdown();
}
