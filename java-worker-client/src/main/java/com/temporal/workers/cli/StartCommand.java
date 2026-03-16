package com.temporal.workers.cli;

import com.temporal.workers.helloworld.HelloResult;
import com.temporal.workers.helloworld.HelloWorldWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import picocli.CommandLine;

import java.util.concurrent.CompletableFuture;

/**
 * Starts a new Hello World workflow execution.
 */
@CommandLine.Command(
        name = "start",
        description = "Start a new Hello World workflow",
        mixinStandardHelpOptions = true
)
public class StartCommand implements Runnable {

    @CommandLine.Parameters(
            index = "0",
            defaultValue = "World",
            description = "Name to greet (default: World)"
    )
    private String name;

    @CommandLine.Option(
            names = {"--id"},
            description = "Custom workflow ID (default: auto-generated from name)"
    )
    private String workflowId;

    @CommandLine.Option(
            names = {"--queue", "-q"},
            defaultValue = "hello-world-queue",
            description = "Task queue (default: hello-world-queue)"
    )
    private String taskQueue;

    @CommandLine.Option(
            names = {"--wait", "-w"},
            description = "Wait for the workflow to complete and print the result"
    )
    private boolean wait;

    @Override
    public void run() {
        try {
            WorkflowClient client = ClientFactory.create();

            if (workflowId == null || workflowId.isBlank()) {
                workflowId = "hello-world-" + name.toLowerCase().replace(' ', '-')
                        + "-" + System.currentTimeMillis();
            }

            HelloWorldWorkflow workflow = client.newWorkflowStub(
                    HelloWorldWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId(workflowId)
                            .setTaskQueue(taskQueue)
                            .build()
            );

            System.out.printf("Starting HelloWorldWorkflow for '%s' …%n", name);
            System.out.printf("  Workflow ID : %s%n", workflowId);
            System.out.printf("  Task Queue  : %s%n", taskQueue);
            System.out.println();

            if (wait) {
                // Synchronous — blocks until workflow completes
                HelloResult result = workflow.run(name);
                System.out.printf("✅ %s%n", result.getGreeting());
                System.out.printf("   Steps completed: %d%n", result.getStepsCompleted());
            } else {
                // Async — fire and forget
                WorkflowClient.start(workflow::run, name);
                System.out.println("🚀 Workflow started (async).");
                System.out.println();
                System.out.println("Track progress:");
                System.out.printf("  temporal-cli status  --id %s%n", workflowId);
                System.out.printf("  temporal-cli result  --id %s%n", workflowId);
                System.out.printf("  temporal-cli describe --id %s%n", workflowId);
            }

        } catch (Exception e) {
            System.err.printf("❌ Failed to start workflow: %s%n", e.getMessage());
            System.exit(1);
        } finally {
            ClientFactory.shutdown();
        }
    }
}
