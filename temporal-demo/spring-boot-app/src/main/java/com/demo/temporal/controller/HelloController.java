package com.demo.temporal.controller;

import com.demo.temporal.shared.JavaHelloWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HelloController {

    private final WorkflowClient workflowClient;

    public HelloController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    /**
     * Sends a hello message to BOTH the Java and Python Temporal workers
     * and returns their responses.
     */
    @PostMapping("/hello")
    public Map<String, Object> sayHello(@RequestBody Map<String, String> request) {
        String name = request.getOrDefault("name", "World");
        Map<String, Object> response = new LinkedHashMap<>();

        // ── 1. Trigger Java Worker (typed stub) ─────────────────────
        try {
            JavaHelloWorkflow javaWorkflow = workflowClient.newWorkflowStub(
                    JavaHelloWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId("java-hello-" + UUID.randomUUID())
                            .setTaskQueue("java-hello-queue")
                            .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                            .build());

            String javaResult = javaWorkflow.sayHello(name);
            response.put("javaWorker", Map.of(
                    "status", "SUCCESS",
                    "message", javaResult,
                    "taskQueue", "java-hello-queue"));
        } catch (Exception e) {
            response.put("javaWorker", Map.of(
                    "status", "ERROR",
                    "message", e.getMessage(),
                    "taskQueue", "java-hello-queue"));
        }

        // ── 2. Trigger Python Worker (untyped stub) ─────────────────
        try {
            WorkflowStub pythonWorkflow = workflowClient.newUntypedWorkflowStub(
                    "PythonHelloWorkflow",
                    WorkflowOptions.newBuilder()
                            .setWorkflowId("python-hello-" + UUID.randomUUID())
                            .setTaskQueue("python-hello-queue")
                            .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                            .build());

            WorkflowExecution execution = pythonWorkflow.start(name);
            String pythonResult = pythonWorkflow.getResult(String.class);
            response.put("pythonWorker", Map.of(
                    "status", "SUCCESS",
                    "message", pythonResult,
                    "workflowId", execution.getWorkflowId(),
                    "taskQueue", "python-hello-queue"));
        } catch (Exception e) {
            response.put("pythonWorker", Map.of(
                    "status", "ERROR",
                    "message", e.getMessage(),
                    "taskQueue", "python-hello-queue"));
        }

        response.put("temporalUi", "http://localhost:8080");
        return response;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "temporal-spring-app");
    }
}
