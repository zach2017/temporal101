package com.demo.temporal.controller;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for async (fire-and-forget) and long-running workflow demos.
 *
 * Async pattern:
 *   POST /api/async         → starts workflow, returns workflowId immediately
 *   GET  /api/async/{id}/status → queries workflow status without blocking
 *
 * Long-running pattern:
 *   POST /api/pipeline         → starts ETL pipeline, returns workflowId immediately
 *   GET  /api/pipeline/{id}/progress → queries pipeline progress without blocking
 *   GET  /api/pipeline/{id}/result   → blocks until workflow completes, returns result
 *
 * Both Java and Python workers are supported via the "worker" request parameter.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AsyncController {

    private final WorkflowClient workflowClient;

    public AsyncController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    // ════════════════════════════════════════════════════════════
    //  ASYNC (FIRE-AND-FORGET) WORKFLOWS
    // ════════════════════════════════════════════════════════════

    /**
     * Start an async workflow — returns immediately with the workflowId.
     * The workflow runs in the background on the chosen worker.
     */
    @PostMapping("/async")
    public Map<String, Object> startAsync(@RequestBody Map<String, String> request) {
        String jobName = request.getOrDefault("jobName", "default-job");
        String workerLang = request.getOrDefault("worker", "java");

        String taskQueue = workerLang.equals("python") ? "python-hello-queue" : "java-hello-queue";
        String workflowType = "AsyncProcessingWorkflow";
        String workflowId = "async-" + workerLang + "-" + UUID.randomUUID();

        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(
                    workflowType,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId(workflowId)
                            .setTaskQueue(taskQueue)
                            .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                            .build());

            // Fire-and-forget: start() returns immediately
            stub.start(jobName);

            return Map.of(
                    "status", "STARTED",
                    "workflowId", workflowId,
                    "worker", workerLang,
                    "taskQueue", taskQueue,
                    "statusUrl", "/api/async/" + workflowId + "/status");
        } catch (Exception e) {
            return Map.of(
                    "status", "ERROR",
                    "message", e.getMessage());
        }
    }

    /**
     * Query the status of a running async workflow.
     * This does NOT block — it reads the current status via Temporal query.
     */
    @GetMapping("/async/{workflowId}/status")
    public Map<String, Object> getAsyncStatus(@PathVariable String workflowId) {
        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowId);
            String status = stub.query("getStatus", String.class);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("workflowId", workflowId);
            response.put("status", status);

            // If completed, also fetch the final result
            if ("COMPLETED".equals(status)) {
                try {
                    String result = stub.getResult(String.class);
                    response.put("result", result);
                } catch (Exception ignored) {
                    // Workflow may still be completing
                }
            }

            return response;
        } catch (Exception e) {
            return Map.of(
                    "workflowId", workflowId,
                    "status", "UNKNOWN",
                    "error", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    //  LONG-RUNNING ETL PIPELINE WORKFLOWS
    // ════════════════════════════════════════════════════════════

    /**
     * Start a long-running ETL pipeline — returns immediately with workflowId.
     * The pipeline runs Extract → Transform → Load → Notify in sequence.
     */
    @PostMapping("/pipeline")
    public Map<String, Object> startPipeline(@RequestBody Map<String, String> request) {
        String source = request.getOrDefault("source", "sales_database");
        String workerLang = request.getOrDefault("worker", "java");

        String taskQueue = workerLang.equals("python") ? "python-hello-queue" : "java-hello-queue";
        String workflowType = "LongRunningWorkflow";
        String workflowId = "pipeline-" + workerLang + "-" + UUID.randomUUID();

        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(
                    workflowType,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId(workflowId)
                            .setTaskQueue(taskQueue)
                            .setWorkflowExecutionTimeout(Duration.ofMinutes(30))
                            .build());

            stub.start(source);

            return Map.of(
                    "status", "STARTED",
                    "workflowId", workflowId,
                    "source", source,
                    "worker", workerLang,
                    "taskQueue", taskQueue,
                    "progressUrl", "/api/pipeline/" + workflowId + "/progress",
                    "resultUrl", "/api/pipeline/" + workflowId + "/result");
        } catch (Exception e) {
            return Map.of(
                    "status", "ERROR",
                    "message", e.getMessage());
        }
    }

    /**
     * Query pipeline progress without blocking.
     * Returns: "STATUS|percentage|description"
     */
    @GetMapping("/pipeline/{workflowId}/progress")
    public Map<String, Object> getPipelineProgress(@PathVariable String workflowId) {
        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowId);
            String raw = stub.query("getProgress", String.class);

            // Parse "STATUS|percentage|description"
            String[] parts = raw.split("\\|", 3);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("workflowId", workflowId);
            response.put("state", parts.length > 0 ? parts[0] : "UNKNOWN");
            response.put("percentage", parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
            response.put("description", parts.length > 2 ? parts[2] : raw);

            // If completed, also fetch the final result
            if ("COMPLETED".equals(parts[0])) {
                try {
                    String result = stub.getResult(String.class);
                    response.put("result", result);
                } catch (Exception ignored) {
                }
            }

            return response;
        } catch (Exception e) {
            return Map.of(
                    "workflowId", workflowId,
                    "state", "UNKNOWN",
                    "percentage", 0,
                    "error", e.getMessage());
        }
    }

    /**
     * Block until the pipeline finishes and return the result.
     * Useful for clients that want to wait for completion.
     */
    @GetMapping("/pipeline/{workflowId}/result")
    public Map<String, Object> getPipelineResult(@PathVariable String workflowId) {
        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowId);
            String result = stub.getResult(String.class);

            return Map.of(
                    "workflowId", workflowId,
                    "status", "COMPLETED",
                    "result", result);
        } catch (Exception e) {
            return Map.of(
                    "workflowId", workflowId,
                    "status", "ERROR",
                    "message", e.getMessage());
        }
    }
}
