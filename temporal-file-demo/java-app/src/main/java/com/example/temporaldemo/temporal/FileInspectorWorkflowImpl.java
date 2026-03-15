package com.example.temporaldemo.temporal;

import com.example.temporaldemo.model.FileResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class FileInspectorWorkflowImpl implements FileInspectorWorkflow {
    @Override
    public FileResult inspect(String filename, String targetWorker) {
        String taskQueue = switch (targetWorker) {
            case "python" -> "python-file-inspector";
            case "java" -> "java-file-inspector";
            default -> throw new IllegalArgumentException("Unknown worker: " + targetWorker);
        };

        FileInspectorActivities activities = Workflow.newActivityStub(
                FileInspectorActivities.class,
                ActivityOptions.newBuilder()
                        .setStartToCloseTimeout(Duration.ofSeconds(10))
                        .setTaskQueue(taskQueue)
                        .build()
        );

        return activities.inspect(filename);
    }
}
