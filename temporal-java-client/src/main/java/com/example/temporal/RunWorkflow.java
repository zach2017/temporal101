package com.example.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

import java.util.UUID;

/**
 * CLI client for HelloWorldWorkflow.
 *
 * MODES:
 *
 *   Start a new workflow (blocks until complete):
 *     java -jar target/temporal-hello-client.jar start [name] [taskQueue]
 *
 *   Start async (returns immediately, prints workflow ID):
 *     java -jar target/temporal-hello-client.jar start-async [name] [taskQueue]
 *
 *   Fetch result of a running/completed workflow by ID:
 *     java -jar target/temporal-hello-client.jar result <workflowId>
 *
 * Environment variables:
 *   TEMPORAL_HOST   (default: localhost)
 *   TEMPORAL_PORT   (default: 7233)
 */
public class RunWorkflow {

    public static void main(String[] args) {
        String envHost  = System.getenv("TEMPORAL_HOST");
        String envPort  = System.getenv("TEMPORAL_PORT");
        String hostname = (envHost != null && !envHost.isBlank()) ? envHost.trim() : "localhost";
        String port     = (envPort != null && !envPort.isBlank()) ? envPort.trim() : "7233";
        String host     = hostname + ":" + port;

        String mode = args.length > 0 ? args[0] : "start";

        System.out.printf("Connecting to Temporal at %s ...%n", host);

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(host).build()
        );
        WorkflowClient client = WorkflowClient.newInstance(service);

        switch (mode) {

            case "result": {
                // ── Attach to an existing workflow and wait for / print its result ──
                if (args.length < 2) {
                    System.err.println("Usage: result <workflowId>");
                    service.shutdown();
                    System.exit(1);
                }
                String workflowId = args[1];
                System.out.printf("Fetching result for workflow id=%s ...%n", workflowId);

                // Untyped stub — no task queue needed, just the ID
                io.temporal.client.WorkflowStub stub =
                        client.newUntypedWorkflowStub(workflowId, java.util.Optional.empty(), java.util.Optional.empty());

                String result = stub.getResult(String.class);
                System.out.printf("Result: %s%n", result);
                break;
            }

            case "start-async": {
                // ── Fire and forget — returns immediately with the workflow ID ──
                String name      = args.length > 1 ? args[1] : "World";
                String taskQueue = args.length > 2 ? args[2] : "hello-world-queue";
                String workflowId = "hello-" + name + "-" + UUID.randomUUID().toString().substring(0, 8);

                WorkflowOptions options = WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(taskQueue)
                        .build();

                HelloWorldWorkflow workflow = client.newWorkflowStub(HelloWorldWorkflow.class, options);
                WorkflowClient.start(workflow::run, name);

                System.out.printf("Workflow started (async)  id=%s%n", workflowId);
                System.out.printf("Fetch result later with:%n");
                System.out.printf("  java -jar target/temporal-hello-client.jar result %s%n", workflowId);
                break;
            }

            default: // "start" — synchronous, blocks until done
            case "start": {
                String name      = args.length > 1 ? args[1] : "World";
                String taskQueue = args.length > 2 ? args[2] : "hello-world-queue";
                String workflowId = "hello-" + name + "-" + UUID.randomUUID().toString().substring(0, 8);

                WorkflowOptions options = WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(taskQueue)
                        .build();

                HelloWorldWorkflow workflow = client.newWorkflowStub(HelloWorldWorkflow.class, options);

                System.out.printf("Starting workflow  id=%s  taskQueue=%s  name=%s%n",
                        workflowId, taskQueue, name);

                String result = workflow.run(name);
                System.out.printf("Result: %s%n", result);
                break;
            }
        }

        service.shutdown();
        System.exit(0);
    }
}
