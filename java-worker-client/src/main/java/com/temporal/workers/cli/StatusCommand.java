package com.temporal.workers.cli;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.client.WorkflowClient;
import picocli.CommandLine;

/**
 * Checks the current status of a workflow execution.
 */
@CommandLine.Command(
        name = "status",
        description = "Check the status of a workflow",
        mixinStandardHelpOptions = true
)
public class StatusCommand implements Runnable {

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

            DescribeWorkflowExecutionResponse desc = client.getWorkflowServiceStubs()
                    .blockingStub()
                    .describeWorkflowExecution(
                            DescribeWorkflowExecutionRequest.newBuilder()
                                    .setNamespace(client.getOptions().getNamespace())
                                    .setExecution(
                                            io.temporal.api.common.v1.WorkflowExecution.newBuilder()
                                                    .setWorkflowId(workflowId)
                                                    .build()
                                    )
                                    .build()
                    );

            // Get status from the execution info
            var execInfo = desc.getWorkflowExecutionInfo();
            WorkflowExecutionStatus status = execInfo.getStatus();

            String emoji = switch (status) {
                case WORKFLOW_EXECUTION_STATUS_RUNNING     -> "🔄";
                case WORKFLOW_EXECUTION_STATUS_COMPLETED   -> "✅";
                case WORKFLOW_EXECUTION_STATUS_FAILED      -> "❌";
                case WORKFLOW_EXECUTION_STATUS_CANCELED    -> "🚫";
                case WORKFLOW_EXECUTION_STATUS_TERMINATED  -> "⛔";
                case WORKFLOW_EXECUTION_STATUS_TIMED_OUT   -> "⏰";
                default                                    -> "❓";
            };

            System.out.printf("%s Workflow: %s%n", emoji, workflowId);
            System.out.printf("   Status     : %s%n", status);
            System.out.printf("   Run ID     : %s%n", execInfo.getExecution().getRunId());
            System.out.printf("   Type       : %s%n", execInfo.getType().getName());
            System.out.printf("   Task Queue : %s%n", execInfo.getTaskQueue());
            System.out.printf("   Start Time : %s%n", execInfo.getStartTime());

            if (execInfo.hasCloseTime()) {
                System.out.printf("   Close Time : %s%n", execInfo.getCloseTime());
            }

            System.out.printf("   History Len: %d events%n", execInfo.getHistoryLength());

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
