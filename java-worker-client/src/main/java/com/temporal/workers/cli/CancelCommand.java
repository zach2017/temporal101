package com.temporal.workers.cli;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import picocli.CommandLine;

/**
 * Requests graceful cancellation of a running workflow.
 * The workflow can catch the cancellation and run cleanup logic.
 */
@CommandLine.Command(
        name = "cancel",
        description = "Cancel a running workflow (graceful)",
        mixinStandardHelpOptions = true
)
public class CancelCommand implements Runnable {

    @CommandLine.Option(
            names = {"--id", "-i"},
            required = true,
            description = "Workflow ID"
    )
    private String workflowId;

    @Override
    public void run() {
        try {
            WorkflowClient client = ClientFactory.create();
            WorkflowStub stub = client.newUntypedWorkflowStub(workflowId);

            System.out.printf("Requesting cancellation of workflow '%s' …%n", workflowId);
            stub.cancel();
            System.out.printf("🚫 Cancellation requested for workflow '%s'.%n", workflowId);
            System.out.println("   The workflow will stop at the next cancellation check point.");

        } catch (io.grpc.StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                System.err.printf("❌ Workflow '%s' not found.%n", workflowId);
            } else {
                System.err.printf("❌ Error: %s%n", e.getStatus().getDescription());
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.printf("❌ Error: %s%n", e.getMessage());
            System.exit(1);
        } finally {
            ClientFactory.shutdown();
        }
    }
}
