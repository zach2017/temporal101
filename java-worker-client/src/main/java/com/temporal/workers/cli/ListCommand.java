package com.temporal.workers.cli;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest;
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsResponse;
import io.temporal.client.WorkflowClient;
import picocli.CommandLine;

/**
 * Lists recent workflow executions with optional filtering by
 * workflow type and status.
 */
@CommandLine.Command(
        name = "list",
        description = "List recent workflow executions",
        mixinStandardHelpOptions = true
)
public class ListCommand implements Runnable {

    @CommandLine.Option(
            names = {"--type"},
            description = "Filter by workflow type name (e.g. HelloWorldWorkflow)"
    )
    private String workflowType;

    @CommandLine.Option(
            names = {"--status", "-s"},
            description = "Filter by status: RUNNING, COMPLETED, FAILED, CANCELED, TERMINATED, TIMED_OUT"
    )
    private String statusFilter;

    @CommandLine.Option(
            names = {"--limit", "-n"},
            defaultValue = "20",
            description = "Max results (default: 20)"
    )
    private int limit;

    @Override
    public void run() {
        try {
            WorkflowClient client = ClientFactory.create();
            var blockingStub = client.getWorkflowServiceStubs().blockingStub();
            String namespace = client.getOptions().getNamespace();

            // Build query string for the visibility API
            StringBuilder query = new StringBuilder();

            if (workflowType != null && !workflowType.isBlank()) {
                query.append(String.format("WorkflowType = '%s'", workflowType));
            }

            if (statusFilter != null && !statusFilter.isBlank()) {
                String normalized = statusFilter.toUpperCase().trim();
                if (!normalized.startsWith("WORKFLOW_EXECUTION_STATUS_")) {
                    normalized = "Running".equalsIgnoreCase(statusFilter) ? "Running"
                            : "Completed".equalsIgnoreCase(statusFilter) ? "Completed"
                            : "Failed".equalsIgnoreCase(statusFilter) ? "Failed"
                            : "Canceled".equalsIgnoreCase(statusFilter) ? "Canceled"
                            : "Terminated".equalsIgnoreCase(statusFilter) ? "Terminated"
                            : "TimedOut".equalsIgnoreCase(statusFilter) ? "TimedOut"
                            : statusFilter;
                }
                if (query.length() > 0) {
                    query.append(" AND ");
                }
                query.append(String.format("ExecutionStatus = '%s'", normalized));
            }

            ListWorkflowExecutionsResponse response = blockingStub
                    .listWorkflowExecutions(
                            ListWorkflowExecutionsRequest.newBuilder()
                                    .setNamespace(namespace)
                                    .setPageSize(limit)
                                    .setQuery(query.toString())
                                    .build()
                    );

            if (response.getExecutionsCount() == 0) {
                System.out.println("No workflow executions found.");
                if (query.length() > 0) {
                    System.out.printf("  (query: %s)%n", query);
                }
                return;
            }

            // Header
            System.out.printf("%-44s  %-28s  %-14s  %s%n",
                    "WORKFLOW ID", "TYPE", "STATUS", "START TIME");
            System.out.println("─".repeat(120));

            for (WorkflowExecutionInfo exec : response.getExecutionsList()) {
                String id = exec.getExecution().getWorkflowId();
                String type = exec.getType().getName();
                WorkflowExecutionStatus status = exec.getStatus();
                String startTime = exec.getStartTime().toString();

                // Truncate long IDs
                if (id.length() > 42) {
                    id = id.substring(0, 39) + "…";
                }
                if (type.length() > 26) {
                    type = type.substring(0, 23) + "…";
                }

                String statusStr = switch (status) {
                    case WORKFLOW_EXECUTION_STATUS_RUNNING     -> "🔄 Running";
                    case WORKFLOW_EXECUTION_STATUS_COMPLETED   -> "✅ Completed";
                    case WORKFLOW_EXECUTION_STATUS_FAILED      -> "❌ Failed";
                    case WORKFLOW_EXECUTION_STATUS_CANCELED    -> "🚫 Canceled";
                    case WORKFLOW_EXECUTION_STATUS_TERMINATED  -> "⛔ Terminated";
                    case WORKFLOW_EXECUTION_STATUS_TIMED_OUT   -> "⏰ TimedOut";
                    default                                    -> "❓ Unknown";
                };

                System.out.printf("%-44s  %-28s  %-14s  %s%n", id, type, statusStr, startTime);
            }

            System.out.println();
            System.out.printf("Showing %d of %d execution(s).%n",
                    response.getExecutionsCount(),
                    response.getExecutionsCount());

        } catch (Exception e) {
            System.err.printf("❌ Error: %s%n", e.getMessage());
            System.exit(1);
        } finally {
            ClientFactory.shutdown();
        }
    }
}
