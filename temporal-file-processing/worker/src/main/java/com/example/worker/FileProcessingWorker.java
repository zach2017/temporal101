package com.example.worker;

import com.example.shared.SharedConstants;
import com.example.worker.activity.FileProcessingActivitiesImpl;
import com.example.worker.workflow.FileProcessingWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * Starts a Temporal Worker that polls the FILE_PROCESSING_TASK_QUEUE.
 *
 * Run:
 *   mvn -pl worker exec:java
 *
 * Prerequisites:
 *   Temporal dev server on localhost:7233 → temporal server start-dev
 */
public class FileProcessingWorker {

    public static void main(String[] args) {

        // 1. gRPC connection to local Temporal Service
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();

        // 2. Temporal Client used internally by the WorkerFactory
        WorkflowClient client = WorkflowClient.newInstance(service);

        // 3. Factory → Worker bound to the shared Task Queue
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(SharedConstants.TASK_QUEUE);

        // 4. Register Workflow and Activity implementations
        worker.registerWorkflowImplementationTypes(FileProcessingWorkflowImpl.class);
        worker.registerActivitiesImplementations(new FileProcessingActivitiesImpl());

        // 5. Start polling (blocks until SIGTERM / Ctrl-C)
        factory.start();
        System.out.println("Worker started — polling task queue: "
                + SharedConstants.TASK_QUEUE);
    }
}
