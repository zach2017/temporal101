package com.example.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

import java.util.UUID;

/**
 * CLI client that submits a HelloWorldWorkflow execution to Temporal.
 *
 * Usage:
 *   java -jar target/temporal-hello-client.jar [name] [taskQueue] [host]
 *
 * Defaults:
 *   name       = World
 *   taskQueue  = hello-world-queue
 *   host       = localhost:7233
 */
public class RunWorkflow {

    public static void main(String[] args) {
        // Parse CLI args with sensible defaults
        String name      = args.length > 0 ? args[0] : "World";
        String taskQueue = args.length > 1 ? args[1] : "hello-world-queue";
        String host      = args.length > 2 ? args[2] : "localhost:7233";

        System.out.printf("Connecting to Temporal at %s ...%n", host);

        // 1. Connect to the Temporal server
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(host)
                        .build()
        );

        // 2. Create a workflow client
        WorkflowClient client = WorkflowClient.newInstance(service);

        // 3. Build a typed workflow stub (no worker needed on this side)
        String workflowId = "hello-" + name + "-" + UUID.randomUUID().toString().substring(0, 8);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .build();

        HelloWorldWorkflow workflow = client.newWorkflowStub(HelloWorldWorkflow.class, options);

        System.out.printf("Starting workflow  id=%s  taskQueue=%s  name=%s%n",
                workflowId, taskQueue, name);

        // 4. Execute synchronously and print result
        String result = workflow.run(name);

        System.out.printf("Result: %s%n", result);

        service.shutdown();
    }
}
