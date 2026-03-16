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
 *
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

    // ── Status label helpers ────────────────────────────────────────────────
    private static final Map<WorkflowExecutionStatus, String> STATUS_LABELS = new HashMap<>();
    static {
        STATUS_LABELS.put(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING,          "🔄 RUNNING");
        STATUS_LABELS.put(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED,        "✅ COMPLETED");
        STATUS_LABELS.put(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_FAILED,           "❌ FAILED");
        STATUS_LABELS.put(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CANCELED,         "🚫 CANCELED");
        STATUS_LABELS.put(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TERMINATED,       "⛔ TERMINATED");
        STATUS_LABELS.put(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TIMED_OUT,        "⏰ TIMED_OUT");
        STATUS_LABELS.put(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW, "➡️  CONTINUED_AS_NEW");
    }

    public static void main(String[] args) throws Exception {

        // ── Resolve host from env ────────────────────────────────────────────
        String envHost = System.getenv("TEMPORAL_HOST");
        String envPort = System.getenv("TEMPORAL_PORT");
        String hostname = (envHost != null && !envHost.isBlank()) ? envHost.trim() : "localhost";
        String port     = (envPort != null && !envPort.isBlank()) ? envPort.trim() : "7233";
        String host     = hostname + ":" + port;

        // ── Parse command ────────────────────────────────────────────────────
        String command = args.length > 0 ? args[0] : "start";

        // ── Parse named flags ────────────────────────────────────────────────
        String jobId      = flag(args, "--job-id",      "job-" + UUID.randomUUID().toString().substring(0, 8));
        String taskQueue  = flag(args, "--task-queue",  "long-running-queue");
        String workflowId = flag(args, "--workflow-id", null);
        int    steps      = Integer.parseInt(flag(args, "--steps", "10"));

        System.out.printf("Connecting to Temporal at %s ...%n", host);

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(host).build()
        );
        WorkflowClient client = WorkflowClient.newInstance(service);

        switch (command) {

            // ── start: synchronous, blocks until workflow completes ──────────
            case "start": {
                String wfId   = "long-running-" + jobId;
                Map<String, Object> payload = buildPayload(jobId, steps);

                WorkflowOptions opts = WorkflowOptions.newBuilder()
                        .setWorkflowId(wfId)
                        .setTaskQueue(taskQueue)
                        .build();

                LongRunningWorkflow wf = client.newWorkflowStub(LongRunningWorkflow.class, opts);

                System.out.printf("Starting workflow (sync)%n");
                System.out.printf("  Workflow ID : %s%n", wfId);
                System.out.printf("  Job ID      : %s%n", jobId);
                System.out.printf("  Steps       : %d  (~%ds total)%n", steps, steps * 2);
                System.out.printf("Waiting for result ...%n%n");

                String result = wf.run(payload);
                System.out.printf("Result: %s%n", result);
                break;
            }

            // ── start-async: fire and forget, print workflow ID ──────────────
            case "start-async": {
                String wfId   = "long-running-" + jobId;
                Map<String, Object> payload = buildPayload(jobId, steps);

                WorkflowOptions opts = WorkflowOptions.newBuilder()
                        .setWorkflowId(wfId)
                        .setTaskQueue(taskQueue)
                        .build();

                LongRunningWorkflow wf = client.newWorkflowStub(LongRunningWorkflow.class, opts);
                WorkflowClient.start(wf::run, payload);

                System.out.printf("Workflow started (async)!%n");
                System.out.printf("  Workflow ID : %s%n", wfId);
                System.out.printf("  Job ID      : %s%n", jobId);
                System.out.printf("  Steps       : %d  (~%ds total)%n%n", steps, steps * 2);
                System.out.printf("Check status:%n");
                System.out.printf("  java -jar target/temporal-longrunning-client.jar status --workflow-id %s%n", wfId);
                System.out.printf("Fetch result (blocks until done):%n");
                System.out.printf("  java -jar target/temporal-longrunning-client.jar result --workflow-id %s%n", wfId);
                break;
            }

            // ── status: describe execution without fetching result ───────────
            case "status": {
                requireFlag("--workflow-id", workflowId);

                DescribeWorkflowExecutionResponse desc = service.blockingStub()
                        .describeWorkflowExecution(
                                DescribeWorkflowExecutionRequest.newBuilder()
                                        .setNamespace("default")
                                        .setExecution(WorkflowExecution.newBuilder()
                                                .setWorkflowId(workflowId)
                                                .build())
                                        .build()
                        );

                WorkflowExecutionStatus status = desc.getWorkflowExecutionInfo().getStatus();
                String label = STATUS_LABELS.getOrDefault(status, status.name());

                long startMs  = desc.getWorkflowExecutionInfo().getStartTime().getSeconds() * 1000;
                long closeMs  = desc.getWorkflowExecutionInfo().getCloseTime().getSeconds() * 1000;

                System.out.printf("Workflow ID : %s%n", workflowId);
                System.out.printf("Status      : %s%n", label);
                System.out.printf("Started at  : %s%n", Instant.ofEpochMilli(startMs));

                if (status != WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING) {
                    System.out.printf("Closed at   : %s%n", Instant.ofEpochMilli(closeMs));
                } else {
                    long elapsed = (System.currentTimeMillis() - startMs) / 1000;
                    System.out.printf("Running for : %ds%n", elapsed);
                    System.out.printf("%nFetch result when ready:%n");
                    System.out.printf("  java -jar target/temporal-longrunning-client.jar result --workflow-id %s%n", workflowId);
                }
                break;
            }

            // ── result: attach to workflow and block until result available ──
            case "result": {
                requireFlag("--workflow-id", workflowId);

                System.out.printf("Attaching to workflow: %s%n", workflowId);
                System.out.printf("Waiting for result (blocks if still running) ...%n%n");

                WorkflowStub stub = client.newUntypedWorkflowStub(
                        workflowId,
                        Optional.empty(),
                        Optional.empty()
                );

                String result = stub.getResult(String.class);
                System.out.printf("Result: %s%n", result);
                break;
            }

            default:
                printUsage();
                System.exit(1);
        }

        service.shutdown();
        System.exit(0);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Object> buildPayload(String jobId, int steps) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("job_id", jobId);
        payload.put("steps",  steps);
        return payload;
    }

    /** Parse --flag value from args array, return defaultValue if not found. */
    private static String flag(String[] args, String name, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) return args[i + 1];
        }
        return defaultValue;
    }

    private static void requireFlag(String name, String value) {
        if (value == null || value.isBlank()) {
            System.err.printf("Error: %s is required for this command.%n", name);
            printUsage();
            System.exit(1);
        }
    }

    private static void printUsage() {
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
