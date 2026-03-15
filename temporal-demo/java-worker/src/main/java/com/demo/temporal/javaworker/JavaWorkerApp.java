package com.demo.temporal.javaworker;

import com.demo.temporal.javaworker.activity.GreetingActivitiesImpl;
import com.demo.temporal.javaworker.activity.ProcessingActivitiesImpl;
import com.demo.temporal.javaworker.workflow.AsyncProcessingWorkflowImpl;
import com.demo.temporal.javaworker.workflow.JavaHelloWorkflowImpl;
import com.demo.temporal.javaworker.workflow.LongRunningWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaWorkerApp {

    private static final Logger log = LoggerFactory.getLogger(JavaWorkerApp.class);
    private static final String TASK_QUEUE = "java-hello-queue";

    public static void main(String[] args) {
        String temporalAddress = System.getenv("TEMPORAL_ADDRESS");
        if (temporalAddress == null || temporalAddress.isBlank()) {
            temporalAddress = "localhost:7233";
        }

        log.info("╔══════════════════════════════════════════════╗");
        log.info("║   Temporal Java Worker Starting...           ║");
        log.info("║   Server : {}                   ║", String.format("%-24s", temporalAddress));
        log.info("║   Queue  : {}                   ║", String.format("%-24s", TASK_QUEUE));
        log.info("╚══════════════════════════════════════════════╝");

        // Connect to Temporal Server
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(temporalAddress)
                        .build());

        WorkflowClient client = WorkflowClient.newInstance(service,
                WorkflowClientOptions.newBuilder()
                        .setNamespace("default")
                        .build());

        // Create worker factory and worker
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TASK_QUEUE);

        // Register workflow and activity implementations
        worker.registerWorkflowImplementationTypes(
                JavaHelloWorkflowImpl.class,
                AsyncProcessingWorkflowImpl.class,
                LongRunningWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                new GreetingActivitiesImpl(),
                new ProcessingActivitiesImpl());

        // Start polling the task queue
        factory.start();
        log.info("Java Worker is now polling task queue: {}", TASK_QUEUE);
    }
}
