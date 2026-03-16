package com.temporal.workers.cli;

import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryResponse;
import io.temporal.client.WorkflowClient;
import picocli.CommandLine;

/**
 * Shows detailed information about a workflow execution including
 * configuration, pending activities, and optionally the event history.
 */
@CommandLine.Command(
        name = "describe",
        description = "Show full details of a workflow execution",
        mixinStandardHelpOptions = true
)
public class DescribeCommand implements Runnable {

    @CommandLine.Option(
            names = {"--id", "-i"},
            required = true,
            description = "Workflow ID"
    )
    private String workflowId;

    @CommandLine.Option(
            names = {"--history"},
            description = "Include event history"
    )
    private boolean showHistory;

    @CommandLine.Option(
            names = {"--max-events"},
            defaultValue = "50",
            description = "Max history events to show (default: 50)"
    )
    private int maxEvents;

    @Override
    public void run() {
        try {
            WorkflowClient client = ClientFactory.create();
            var blockingStub = client.getWorkflowServiceStubs().blockingStub();
            String namespace = client.getOptions().getNamespace();

            var execution = io.temporal.api.common.v1.WorkflowExecution.newBuilder()
                    .setWorkflowId(workflowId)
                    .build();

            // ── Describe execution ──────────────────
            DescribeWorkflowExecutionResponse desc = blockingStub
                    .describeWorkflowExecution(
                            DescribeWorkflowExecutionRequest.newBuilder()
                                    .setNamespace(namespace)
                                    .setExecution(execution)
                                    .build()
                    );

            var execInfo = desc.getWorkflowExecutionInfo();

            System.out.println("╔══════════════════════════════════════════════════╗");
            System.out.println("║             Workflow Execution Details           ║");
            System.out.println("╚══════════════════════════════════════════════════╝");
            System.out.println();
            System.out.printf("  Workflow ID  : %s%n", execInfo.getExecution().getWorkflowId());
            System.out.printf("  Run ID       : %s%n", execInfo.getExecution().getRunId());
            System.out.printf("  Type         : %s%n", execInfo.getType().getName());
            System.out.printf("  Status       : %s%n", execInfo.getStatus());
            System.out.printf("  Task Queue   : %s%n", execInfo.getTaskQueue());
            System.out.printf("  Start Time   : %s%n", execInfo.getStartTime());
            if (execInfo.hasCloseTime()) {
                System.out.printf("  Close Time   : %s%n", execInfo.getCloseTime());
            }
            System.out.printf("  History Len  : %d events%n", execInfo.getHistoryLength());

            // ── Pending activities ──────────────────
            int pendingCount = desc.getPendingActivitiesCount();
            if (pendingCount > 0) {
                System.out.println();
                System.out.printf("  Pending Activities (%d):%n", pendingCount);
                for (var pending : desc.getPendingActivitiesList()) {
                    System.out.printf("    • %s  (attempt %d, state=%s)%n",
                            pending.getActivityType().getName(),
                            pending.getAttempt(),
                            pending.getState());
                    if (pending.hasLastHeartbeatTime()) {
                        System.out.printf("      Last heartbeat: %s%n", pending.getLastHeartbeatTime());
                    }
                }
            }

            // ── Event history ───────────────────────
            if (showHistory) {
                System.out.println();
                System.out.println("  ── Event History ──────────────────────────────");

                GetWorkflowExecutionHistoryResponse history = blockingStub
                        .getWorkflowExecutionHistory(
                                GetWorkflowExecutionHistoryRequest.newBuilder()
                                        .setNamespace(namespace)
                                        .setExecution(execution)
                                        .build()
                        );

                int count = 0;
                for (HistoryEvent event : history.getHistory().getEventsList()) {
                    if (count >= maxEvents) {
                        System.out.printf("  … (%d more events, use --max-events to increase)%n",
                                history.getHistory().getEventsCount() - maxEvents);
                        break;
                    }

                    EventType type = event.getEventType();
                    System.out.printf("  %4d  %-50s  %s%n",
                            event.getEventId(),
                            type,
                            event.getEventTime());
                    count++;
                }
            }

            System.out.println();

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
