package com.docprocessing.tika.worker;

import com.docprocessing.tika.activity.TikaDetectionActivity;
import io.github.cdimascio.dotenv.Dotenv;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temporal Worker process – Apache Tika MIME Detection.
 *
 * <p>Listens on the {@code tika-detection-queue} and exposes the
 * {@code detect_file_type_tika} activity.  The Python
 * {@code DocumentIntakeWorkflow} calls this activity as its first
 * step to determine the document type before routing.
 *
 * <p>Run:
 * <pre>
 *   ./gradlew run
 *   # or
 *   java -jar tika-worker.jar
 * </pre>
 */
public final class TikaWorker {

    private static final Logger log = LoggerFactory.getLogger(TikaWorker.class);

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String host = resolve(dotenv, "TEMPORAL_HOST", "host.docker.internal:7233");
        String namespace = resolve(dotenv, "TEMPORAL_NAMESPACE", "default");
        String taskQueue = resolve(dotenv, "TEMPORAL_TIKA_TASK_QUEUE", "tika-detection-queue");

        log.info("Starting Tika Worker: host={} namespace={} queue={}",
                host, namespace, taskQueue);

        // ── Connect to Temporal ──────────────────────────────
        WorkflowServiceStubsOptions serviceOpts = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(host)
                .build();
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOpts);

        WorkflowClientOptions clientOpts = WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build();
        WorkflowClient client = WorkflowClient.newInstance(service, clientOpts);

        // ── Create worker and register the Tika activity ─────
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(taskQueue);

        worker.registerActivitiesImplementations(new TikaDetectionActivity.Impl());

        // ── Start polling ────────────────────────────────────
        factory.start();
        log.info("Tika Worker running — listening on '{}'", taskQueue);
    }

    private static String resolve(Dotenv dotenv, String key, String fallback) {
        String value = dotenv.get(key);
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
