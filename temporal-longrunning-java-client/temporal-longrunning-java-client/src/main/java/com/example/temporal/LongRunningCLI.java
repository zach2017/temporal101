package com.example.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.api.common.v1.WorkflowExecution;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * CLI client for LongRunningWorkflow (Python worker).
 *
 * COMMANDS:
 *   start        Start workflow, block until complete
 *   start-async  Start workflow, return immediately (prints workflow ID)
 *   status       Show current status of a running/completed workflow
 *   result       Attach to workflow and wait for / print result
 *
 * USAGE:
 *   java -jar target/temporal-longrunning-client.jar start       [--job-id X] [--steps N] [--task-queue Q]
 *   java -jar target/temporal-longrunning-client.jar start-async [--job-id X] [--steps N] [--task-queue Q]
 *   java -jar target/temporal-longrunning-client.jar status      --workflow-id <id>
 *   java -jar target/temporal-longrunning-client.jar result      --workflow-id <id>
 *
 * ENV VARS:
 *   TEMPORAL_HOST  (default: localhost)
 *   TEMPORAL_PORT  (default: 7233)
 */
public class LongRunningCLI {

    // ── Java 21: record for parsed CLI args ─────────────────────────────────
    record CliArgs(
        String command,
        String jobId,
        String taskQueue,
        String workflowId,
        int    steps
    ) {}

    // ── Java 21: record for connection config ────────────────────────────────
    record TemporalConfig(String host) {
        static TemporalConfig fromEnv() {
            var envHost = Optional.ofNullable(System.getenv("TEMPORAL_HOST")).filter(s -> !s.isBlank()).orElse("localhost");
            var envPort = Optional.ofNullable(System.getenv("TEMPORAL_PORT")).filter(s -> !s.isBlank()).orElse("7233");
            return new TemporalConfig(envHost + ":" + envPort);
        }
    }

    private static final Map<WorkflowExecutionStatus, String> STATUS_LABELS = Map.of(
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING,          "🔄 RUNNING",
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED,        "✅ COMPLETED",
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_FAILED,           "❌ FAILED",
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CANCELED,         "🚫 CANCELED",
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TERMINATED,       "⛔ TERMINATED",
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TIMED_OUT,        "⏰ TIMED_OUT",
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW, "➡️  CONTINUED_AS_NEW"
    );

    public static void main(String[] args) throws Exception {
        var config = TemporalConfig.fromEnv();
        var cli    = parseArgs(args);

        System.out.printf("Connecting to Temporal at %s ...%n", config.host());

        var service = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder().setTarget(config.host()).build()
        );
        var client = WorkflowClient.newInstance(service);

        // ── Java 21: switch expression (arrow syntax) ────────────────────────
        switch (cli.command()) {

            case "start" -> {
                var wfId    = "long-running-" + cli.jobId();
                var payload = buildPayload(cli.jobId(), cli.steps());
                var opts    = workflowOptions(wfId, cli.taskQueue());
                var wf      = client.newWorkflowStub(LongRunningWorkflow.class, opts);

                System.out.printf("Starting workflow (sync)%n");
                printWorkflowInfo(wfId, cli.jobId(), cli.steps());
                System.out.printf("Waiting for result ...%n%n");

                var result = wf.run(payload);
                System.out.printf("Result: %s%n", result);
            }

            case "start-async" -> {
                var wfId    = "long-running-" + cli.jobId();
                var payload = buildPayload(cli.jobId(), cli.steps());
                var opts    = workflowOptions(wfId, cli.taskQueue());
                var wf      = client.newWorkflowStub(LongRunningWorkflow.class, opts);

                WorkflowClient.start(wf::run, payload);

                System.out.printf("Workflow started (async)!%n");
                printWorkflowInfo(wfId, cli.jobId(), cli.steps());
                System.out.printf("%nCheck status:%n");
                System.out.printf("  java -jar target/temporal-longrunning-client.jar status --workflow-id %s%n", wfId);
                System.out.printf("Fetch result (blocks until done):%n");
                System.out.printf("  java -jar target/temporal-longrunning-client.jar result --workflow-id %s%n", wfId);
            }

            case "status" -> {
                requireArg("--workflow-id", cli.workflowId());

                var desc = service.blockingStub().describeWorkflowExecution(
                    DescribeWorkflowExecutionRequest.newBuilder()
                        .setNamespace("default")
                        .setExecution(WorkflowExecution.newBuilder()
                            .setWorkflowId(cli.workflowId())
                            .build())
                        .build()
                );

                var status  = desc.getWorkflowExecutionInfo().getStatus();
                var label   = STATUS_LABELS.getOrDefault(status, status.name());
                var startMs = desc.getWorkflowExecutionInfo().getStartTime().getSeconds() * 1000;

                System.out.printf("Workflow ID : %s%n", cli.workflowId());
                System.out.printf("Status      : %s%n", label);
                System.out.printf("Started at  : %s%n", Instant.ofEpochMilli(startMs));

                // ── Java 21: pattern-enhanced condition ──────────────────────
                if (status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING) {
                    var elapsed = (System.currentTimeMillis() - startMs) / 1000;
                    System.out.printf("Running for : %ds%n", elapsed);
                    System.out.printf("%nFetch result when ready:%n");
                    System.out.printf("  java -jar target/temporal-longrunning-client.jar result --workflow-id %s%n", cli.workflowId());
                } else {
                    var closeMs = desc.getWorkflowExecutionInfo().getCloseTime().getSeconds() * 1000;
                    System.out.printf("Closed at   : %s%n", Instant.ofEpochMilli(closeMs));
                }
            }

            case "result" -> {
                requireArg("--workflow-id", cli.workflowId());

                System.out.printf("Attaching to workflow: %s%n", cli.workflowId());
                System.out.printf("Waiting for result (blocks if still running) ...%n%n");

                WorkflowStub stub = client.newUntypedWorkflowStub(
                    cli.workflowId(), Optional.empty(), Optional.empty()
                );

                var result = stub.getResult(String.class);
                System.out.printf("Result: %s%n", result);
            }

            default -> {
                printUsage();
                System.exit(1);
            }
        }

        service.shutdown();
        System.exit(0);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static CliArgs parseArgs(String[] args) {
        return new CliArgs(
            args.length > 0 ? args[0] : "start",
            flag(args, "--job-id",      "job-" + UUID.randomUUID().toString().substring(0, 8)),
            flag(args, "--task-queue",  "long-running-queue"),
            flag(args, "--workflow-id", null),
            Integer.parseInt(flag(args, "--steps", "10"))
        );
    }

    private static WorkflowOptions workflowOptions(String workflowId, String taskQueue) {
        return WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId)
            .setTaskQueue(taskQueue)
            .build();
    }

    private static Map<String, Object> buildPayload(String jobId, int steps) {
        var payload = new HashMap<String, Object>();
        payload.put("job_id", jobId);
        payload.put("steps",  steps);
        return payload;
    }

    private static void printWorkflowInfo(String wfId, String jobId, int steps) {
        System.out.printf("  Workflow ID : %s%n", wfId);
        System.out.printf("  Job ID      : %s%n", jobId);
        System.out.printf("  Steps       : %d  (~%ds total)%n", steps, steps * 2);
    }

    private static String flag(String[] args, String name, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) return args[i + 1];
        }
        return defaultValue;
    }

    private static void requireArg(String name, String value) {
        if (value == null || value.isBlank()) {
            System.err.printf("Error: %s is required for this command.%n", name);
            printUsage();
            System.exit(1);
        }
    }

    private static void printUsage() {
        // Java 21: text block
        System.out.println("""
            Usage:
              java -jar target/temporal-longrunning-client.jar <command> [options]

            Commands:
              start        Start workflow and block until result
              start-async  Start workflow and return immediately
              status       Show current status of a workflow
              result       Fetch result (blocks if still running)

            Options:
              --job-id       <string>   Job identifier        (default: random)
              --steps        <int>      Steps to simulate     (default: 10)
              --task-queue   <string>   Task queue name       (default: long-running-queue)
              --workflow-id  <string>   Required for status/result commands

            Env vars:
              TEMPORAL_HOST  (default: localhost)
              TEMPORAL_PORT  (default: 7233)
            """);
    }
}
