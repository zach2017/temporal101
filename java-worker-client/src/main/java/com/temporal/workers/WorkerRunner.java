package com.temporal.workers;

import com.temporal.workers.config.WorkerConfig;
import com.temporal.workers.registry.WorkerRegistration;
import com.temporal.workers.registry.WorkerRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Temporal Worker Runner
 * ======================
 * Connects to the Temporal server and starts one Worker per registered
 * task queue. Workers are discovered through {@link WorkerRegistry}.
 */
public class WorkerRunner {

    private static final Logger logger = LoggerFactory.getLogger(WorkerRunner.class);

    public static void main(String[] args) {
        WorkerConfig config = WorkerConfig.getInstance();

        logger.info("══════════════════════════════════════════════");
        logger.info("  Temporal Java Worker Runner");
        logger.info("══════════════════════════════════════════════");
        logger.info("  {}", config);
        logger.info("══════════════════════════════════════════════");

        // ── Connect to Temporal ─────────────────────
        logger.info("Connecting to Temporal at {} (namespace={}) …",
                config.getServerUrl(), config.getNamespace());

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(config.getServerUrl())
                        .build()
        );

        WorkflowClient client = WorkflowClient.newInstance(
                service,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(config.getNamespace())
                        .build()
        );

        // ── Create worker factory ───────────────────
        WorkerFactory factory = WorkerFactory.newInstance(
                client,
                WorkerFactoryOptions.newBuilder().build()
        );

        // ── Discover and register workers ───────────
        List<WorkerRegistration> registrations = WorkerRegistry.discoverWorkers();

        if (registrations.isEmpty()) {
            logger.error("No workers registered — nothing to do. Exiting.");
            System.exit(1);
        }

        logger.info("Starting {} worker(s) …", registrations.size());

        for (WorkerRegistration reg : registrations) {
            Worker worker = factory.newWorker(reg.taskQueue());

            // Register workflow implementations
            for (Class<?> wfClass : reg.workflows()) {
                worker.registerWorkflowImplementationTypes(wfClass);
            }

            // Register activity implementations
            for (Object activityImpl : reg.activities()) {
                worker.registerActivitiesImplementations(activityImpl);
            }

            logger.info("  → Worker ready on queue: {}", reg.taskQueue());
        }

        // ── Start all workers ───────────────────────
        factory.start();
        logger.info("All workers running. Press Ctrl+C to stop.");

        // Shutdown hook for graceful drain
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received — draining workers …");
            factory.shutdown();
            service.shutdown();
            logger.info("All workers stopped. Goodbye.");
        }));
    }
}
