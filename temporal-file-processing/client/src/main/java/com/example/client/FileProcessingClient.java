package com.example.client;

import com.example.shared.SharedConstants;
import com.example.shared.model.FileProcessingRequest;
import com.example.shared.model.FileProcessingResult;
import com.example.shared.workflow.FileProcessingWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;

import java.util.UUID;

/**
 * Starts a FileProcessingWorkflow execution on the Temporal Service.
 *
 * Run:
 *   mvn -pl client exec:java
 *
 * Prerequisites:
 *   - Temporal dev server on localhost:7233
 *   - Worker already running (to pick up the task)
 */
public class FileProcessingClient {

    public static void main(String[] args) {

        // 1. Connect to local Temporal Service
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);

        // 2. Build the request model
        String jobId = "JOB-" + UUID.randomUUID().toString().substring(0, 8);

        FileProcessingRequest request = new FileProcessingRequest(
                jobId,
                "report-2026-q1.csv",
                "/data/incoming/reports"
        );

        // 3. Create a typed Workflow stub from the shared interface
        FileProcessingWorkflow workflow = client.newWorkflowStub(
                FileProcessingWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(jobId)                    // unique per execution
                        .setTaskQueue(SharedConstants.TASK_QUEUE) // must match Worker
                        .build()
        );

        // 4. Start the Workflow — blocks until it completes
        System.out.println("Starting workflow — " + request);
        FileProcessingResult result = workflow.processFile(request);
        System.out.println("Workflow completed — " + result);
    }
}
