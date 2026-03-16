package com.temporal.workers.cli;

import com.temporal.workers.helloworld.HelloResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import picocli.CommandLine;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Waits for a workflow to complete and prints its result.
 */
@CommandLine.Command(
        name = "result",
        description = "Wait for and retrieve a workflow result",
        mixinStandardHelpOptions = true
)
public class ResultCommand implements Runnable {

    @CommandLine.Option(
            names = {"--id", "-i"},
            required = true,
            description = "Workflow ID"
    )
    private String workflowId;

    @CommandLine.Option(
            names = {"--run-id", "-r"},
            description = "Specific run ID (optional — defaults to latest run)"
    )
    private String runId;

    @CommandLine.Option(
            names = {"--timeout", "-t"},
            defaultValue = "0",
            description = "Max seconds to wait (0 = wait forever, default: 0)"
    )
    private long timeoutSecs;

    @Override
    @SuppressWarnings("deprecation")
    public void run() {
        try {
            WorkflowClient client = ClientFactory.create();

            WorkflowStub stub;
            if (runId != null && !runId.isBlank()) {
                stub = client.newUntypedWorkflowStub(
                        workflowId, Optional.of(runId), Optional.empty());
            } else {
                stub = client.newUntypedWorkflowStub(workflowId);
            }

            System.out.printf("⏳ Waiting for workflow '%s' to complete …%n", workflowId);

            HelloResult result;
            if (timeoutSecs > 0) {
                result = stub.getResult(timeoutSecs, TimeUnit.SECONDS, HelloResult.class);
            } else {
                result = stub.getResult(HelloResult.class);
            }

            System.out.println();
            System.out.printf("✅ %s%n", result.getGreeting());
            System.out.printf("   Steps completed: %d%n", result.getStepsCompleted());

        } catch (Exception e) {
            String msg = e.getMessage();
            if (e instanceof TimeoutException || (msg != null && msg.contains("timeout"))) {
                System.err.printf("⏰ Timed out after %d seconds waiting for workflow '%s'.%n",
                        timeoutSecs, workflowId);
            } else if (msg != null && msg.contains("NOT_FOUND")) {
                System.err.printf("❌ Workflow '%s' not found.%n", workflowId);
            } else {
                System.err.printf("❌ Error: %s%n", msg);
            }
            System.exit(1);
        } finally {
            ClientFactory.shutdown();
        }
    }
}
