package demo.temporal.worker;

import demo.temporal.shared.TaskQueues;
import demo.temporal.worker.activity.FileDetectionActivitiesImpl;
import demo.temporal.worker.activity.FileStorageActivitiesImpl;
import demo.temporal.worker.activity.OcrActivitiesImpl;
import demo.temporal.worker.activity.TextExtractionActivitiesImpl;
import demo.temporal.worker.workflow.FileProcessingWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.worker.WorkerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the File Processor Worker process.
 *
 * <p>
 * Reads connection settings from environment variables:
 * <ul>
 * <li>{@code TEMPORAL_ADDRESS} – gRPC target (default:
 * {@code localhost:7233})</li>
 * <li>{@code TEMPORAL_NAMESPACE} – namespace (default: {@code default})</li>
 * <li>{@code WORKER_MAX_CONCURRENT_ACTIVITIES} – concurrency cap (default:
 * {@code 5})</li>
 * <li>{@code TESSDATA_PREFIX} – Tesseract trained data directory</li>
 * </ul>
 *
 * <p>
 * Registers all Workflow and Activity implementations with a single Worker that
 * polls the {@link TaskQueues#FILE_PROCESSING_TASK_QUEUE}.</p>
 */
public class FileProcessorWorker {

    private static final Logger log = LoggerFactory.getLogger(FileProcessorWorker.class);

    public static void main(String[] args) {
        log.info("═══════════════════════════════════════════════════════");
        log.info("  Temporal File Processor Worker starting…");
        log.info("═══════════════════════════════════════════════════════");

        // ── 1. Read configuration from environment ───────────────
        String temporalAddress = env("TEMPORAL_ADDRESS", "localhost:7233");
        String namespace = env("TEMPORAL_NAMESPACE", "default");
        int maxConcurrent = Integer.parseInt(
                env("WORKER_MAX_CONCURRENT_ACTIVITIES", "5"));

        log.info("Temporal address : {}", temporalAddress);
        log.info("Namespace        : {}", namespace);
        log.info("Max concurrent   : {}", maxConcurrent);
        log.info("Task Queue       : {}", TaskQueues.FILE_PROCESSING_TASK_QUEUE);

        // ── 2. Create gRPC stubs + WorkflowClient ────────────────
        WorkflowServiceStubs serviceStubs = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(temporalAddress)
                        .build());

        WorkflowClient client = WorkflowClient.newInstance(serviceStubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(namespace)
                        .build());

        // ── 3. Create WorkerFactory + Worker ─────────────────────
        WorkerFactory factory = WorkerFactory.newInstance(client,
                WorkerFactoryOptions.newBuilder().build());

        Worker worker = factory.newWorker(
                TaskQueues.FILE_PROCESSING_TASK_QUEUE,
                WorkerOptions.newBuilder()
                        .setMaxConcurrentActivityExecutionSize(maxConcurrent)
                        .setMaxConcurrentWorkflowTaskExecutionSize(maxConcurrent)
                        .build());

        // ── 4. Register Workflow implementations ─────────────────
        worker.registerWorkflowImplementationTypes(
                FileProcessingWorkflowImpl.class
        );

        // ── 5. Register Activity implementations ─────────────────
        worker.registerActivitiesImplementations(
                new FileDetectionActivitiesImpl(),
                new TextExtractionActivitiesImpl(),
                new OcrActivitiesImpl(),
                new FileStorageActivitiesImpl()
        );

        // ── 6. Start polling ─────────────────────────────────────
        factory.start();

        log.info("═══════════════════════════════════════════════════════");
        log.info("  Worker STARTED — polling task queue: {}",
                TaskQueues.FILE_PROCESSING_TASK_QUEUE);
        log.info("═══════════════════════════════════════════════════════");

        // Keep alive — factory.start() launches daemon threads
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received — stopping worker…");
            factory.shutdown();
            serviceStubs.shutdown();
            log.info("Worker stopped cleanly.");
        }));
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
