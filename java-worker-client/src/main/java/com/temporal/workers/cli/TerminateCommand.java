package com.temporal.workers.cli;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import picocli.CommandLine;

/**
 * Terminates a running workflow immediately.
 * Unlike cancel, this does NOT allow the workflow to run cleanup logic.
 */
@CommandLine.Command(
        name = "terminate",
        description = "Terminate a running workflow immediately (no cleanup)",
        mixinStandardHelpOptions = true
)
public class TerminateCommand implements Runnable {

    @CommandLine.Option(
            names = {"--id", "-i"},
            required = true,
            description = "Workflow ID"
    )
    private String workflowId;

    @CommandLine.Option(
            names = {"--reason"},
            defaultValue = "Terminated via CLI",
            description = "Termination reason"
    )
    private String reason;

    @CommandLine.Option(
            names = {"--force", "-f"},
            description = "Skip confirmation prompt"
    )
    private boolean force;

    @Override
    public void run() {
        try {
            if (!force) {
                System.out.printf("⚠️  This will IMMEDIATELY terminate workflow '%s'.%n", workflowId);
                System.out.println("   The workflow will NOT be able to run cleanup logic.");
                System.out.print("   Continue? [y/N] ");
                int ch = System.in.read();
                if (ch != 'y' && ch != 'Y') {
                    System.out.println("Aborted.");
                    return;
                }
            }

            WorkflowClient client = ClientFactory.create();
            WorkflowStub stub = client.newUntypedWorkflowStub(workflowId);

            stub.terminate(reason);
            System.out.printf("⛔ Workflow '%s' terminated. Reason: %s%n", workflowId, reason);

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
