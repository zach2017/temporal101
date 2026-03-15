package com.example.temporaldemo.controller;

import com.example.temporaldemo.model.FileRequest;
import com.example.temporaldemo.model.FileResult;
import com.example.temporaldemo.temporal.AppProperties;
import com.example.temporaldemo.temporal.FileInspectorWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class FileController {
    private final WorkflowClient workflowClient;
    private final AppProperties properties;

    public FileController(WorkflowClient workflowClient, AppProperties properties) {
        this.workflowClient = workflowClient;
        this.properties = properties;
    }

    @PostMapping("/inspect/{worker}")
    public ResponseEntity<?> inspect(@PathVariable String worker, @RequestBody FileRequest request) {
        if (request.getFilename() == null || request.getFilename().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "filename is required"));
        }
        if (!worker.equals("java") && !worker.equals("python")) {
            return ResponseEntity.badRequest().body(Map.of("error", "worker must be java or python"));
        }

        FileInspectorWorkflow workflow = workflowClient.newWorkflowStub(
                FileInspectorWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(properties.getTemporal().getWorkflowTaskQueue())
                        .setWorkflowId("inspect-" + worker + "-" + UUID.randomUUID())
                        .build()
        );

        FileResult result = workflow.inspect(request.getFilename(), worker);
        return ResponseEntity.ok(result);
    }
}
