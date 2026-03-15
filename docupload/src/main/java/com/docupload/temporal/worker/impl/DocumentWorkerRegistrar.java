package com.docupload.temporal.worker.impl;

import com.docupload.temporal.worker.DocumentWorker;
import com.docupload.temporal.workflow.impl.DocumentProcessingWorkflowImpl;
import com.docupload.temporal.activity.impl.NlpAnalysisActivityImpl;
import com.docupload.temporal.activity.impl.SecurityScanActivityImpl;
import com.docupload.temporal.activity.impl.TextExtractionActivityImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ════════════════════════════════════════════════════════════════
 *  Temporal Worker Registrar
 * ════════════════════════════════════════════════════════════════
 *
 * Starts a Temporal Worker that polls DOCUMENT_PROCESSING_TASK_QUEUE
 * for both workflow and activity tasks.
 *
 * HOW TO ADD THIS TO TEMPORAL — STEP BY STEP
 * ────────────────────────────────────────────
 *
 * STEP 1 — Install & start the Temporal dev server
 * ─────────────────────────────────────────────────
 *   macOS:
 *     brew install temporal
 *     temporal server start-dev
 *
 *   Linux / Windows (via Go):
 *     go install go.temporal.io/server/cmd/temporal@latest
 *     temporal server start-dev
 *
 *   Docker (alternative):
 *     docker run --rm -p 7233:7233 -p 8233:8233 \
 *       temporalio/auto-setup:latest \
 *       temporal server start-dev
 *
 *   Verify: open http://localhost:8233 — Temporal Web UI should appear.
 *
 * STEP 2 — Add Temporal dependencies to pom.xml  (already done)
 * ──────────────────────────────────────────────
 *   <dependency>
 *     <groupId>io.temporal</groupId>
 *     <artifactId>temporal-sdk</artifactId>
 *     <version>1.25.0</version>
 *   </dependency>
 *   <dependency>
 *     <groupId>io.temporal</groupId>
 *     <artifactId>temporal-spring-boot-autoconfigure</artifactId>
 *     <version>1.25.0</version>
 *   </dependency>
 *
 * STEP 3 — Configure application.properties  (already done)
 * ──────────────────────────────────────────
 *   spring.temporal.connection.target=127.0.0.1:7233
 *   spring.temporal.namespace=default
 *   spring.temporal.workers-auto-discovery.packages=com.docupload.temporal
 *
 * STEP 4 — Annotate implementations  (already done)
 * ──────────────────────────────────
 *   @WorkflowImpl(taskQueues = "DOCUMENT_PROCESSING_TASK_QUEUE")
 *     on DocumentProcessingWorkflowImpl
 *
 *   @ActivityImpl(taskQueues = "DOCUMENT_PROCESSING_TASK_QUEUE")
 *     on SecurityScanActivityImpl, TextExtractionActivityImpl, NlpAnalysisActivityImpl
 *
 * STEP 5 — Run the Spring Boot application
 * ─────────────────────────────────────────
 *   mvn spring-boot:run
 *
 *   On startup you will see log lines like:
 *     INFO  i.t.i.worker.Worker - Started worker taskQueue=DOCUMENT_PROCESSING_TASK_QUEUE
 *
 * STEP 6 — Submit a workflow via the UI
 * ─────────────────────────────────────
 *   Open http://localhost:8080, choose "Temporal Worker" mode, and upload a file.
 *   Then open http://localhost:8233 to watch the workflow execute in the Temporal UI.
 *
 * STEP 7 — Connect to Temporal Cloud (production)
 * ─────────────────────────────────────────────────
 *   Replace the application.properties entries with:
 *     spring.temporal.connection.target=<namespace>.tmprl.cloud:7233
 *     spring.temporal.namespace=<your-namespace>
 *     spring.temporal.connection.mtls.key-file=/path/to/client.key
 *     spring.temporal.connection.mtls.cert-chain-file=/path/to/client.crt
 *
 * ════════════════════════════════════════════════════════════════
 *
 * NOTE: When temporal-spring-boot-autoconfigure is on the classpath AND
 * workers-auto-discovery.packages is configured, the starter will auto-
 * create the Worker using the @WorkflowImpl / @ActivityImpl annotations.
 * This class provides an EXPLICIT fallback registration so the app still
 * boots and processes workflows even if auto-discovery is disabled.
 */
@Component
public class DocumentWorkerRegistrar implements DocumentWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentWorkerRegistrar.class);

    private final WorkflowClient workflowClient;
    private final SecurityScanActivityImpl  scanActivity;
    private final TextExtractionActivityImpl extractActivity;
    private final NlpAnalysisActivityImpl    nlpActivity;

    private WorkerFactory factory;

    public DocumentWorkerRegistrar(WorkflowClient workflowClient,
                                   SecurityScanActivityImpl scanActivity,
                                   TextExtractionActivityImpl extractActivity,
                                   NlpAnalysisActivityImpl nlpActivity) {
        this.workflowClient  = workflowClient;
        this.scanActivity    = scanActivity;
        this.extractActivity = extractActivity;
        this.nlpActivity     = nlpActivity;
    }

    /**
     * Called automatically after the Spring context is fully initialised.
     * Using ApplicationReadyEvent ensures all beans are wired before we
     * start polling Temporal (avoids dependency ordering issues).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Override
    public void start() {
        log.info("──────────────────────────────────────────────────────────────");
        log.info("  Starting Temporal worker on task queue: {}", TASK_QUEUE);
        log.info("──────────────────────────────────────────────────────────────");

        factory = WorkerFactory.newInstance(workflowClient);

        Worker worker = factory.newWorker(TASK_QUEUE,
                WorkerOptions.newBuilder()
                        // Max concurrent workflow coroutines this worker handles
                        .setMaxConcurrentWorkflowTaskExecutionSize(10)
                        // Max concurrent activity threads
                        .setMaxConcurrentActivityExecutionSize(20)
                        .build());

        // ── Register workflow implementation ──────────────────────────────────
        // STEP: Add new Workflow types here as you build them
        worker.registerWorkflowImplementationTypes(DocumentProcessingWorkflowImpl.class);
        log.info("  ✓ Registered workflow: DocumentProcessingWorkflowImpl");

        // ── Register activity implementations (instances, not classes) ────────
        // STEP: Add new Activity impl instances here as you build them
        worker.registerActivitiesImplementations(scanActivity, extractActivity, nlpActivity);
        log.info("  ✓ Registered activities: SecurityScan, TextExtraction, NlpAnalysis");

        // ── Start polling ─────────────────────────────────────────────────────
        factory.start();
        log.info("  ✓ Worker polling started — task queue: {}", TASK_QUEUE);
        log.info("  ✓ Temporal Web UI: http://localhost:8233");
        log.info("──────────────────────────────────────────────────────────────");
    }

    /**
     * Graceful shutdown: waits up to 30 s for in-flight tasks to complete
     * before tearing down the factory.
     */
    @PreDestroy
    @Override
    public void shutdown() {
        if (factory != null) {
            log.info("Shutting down Temporal worker factory (awaiting in-flight tasks)…");
            factory.shutdown();
            factory.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
            log.info("Temporal worker shut down cleanly.");
        }
    }
}
