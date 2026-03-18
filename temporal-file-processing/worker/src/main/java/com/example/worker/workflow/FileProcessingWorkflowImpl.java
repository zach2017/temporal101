package com.example.worker.workflow;

import com.example.shared.activity.FileProcessingActivities;
import com.example.shared.model.FileProcessingRequest;
import com.example.shared.model.FileProcessingResult;
import com.example.shared.workflow.FileProcessingWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * Workflow implementation — only the Worker needs this class.
 * The Client starts it using the shared interface.
 */
public class FileProcessingWorkflowImpl implements FileProcessingWorkflow {

    private String currentStatus = "INITIALIZED";

    private final FileProcessingActivities activities =
            Workflow.newActivityStub(
                    FileProcessingActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(5))
                            .build()
            );

    @Override
    public FileProcessingResult processFile(FileProcessingRequest request) {
        String jobId        = request.getJobId();
        String fileName     = request.getFileName();
        String fileLocation = request.getFileLocation();

        // Step 1 — Validate
        currentStatus = "VALIDATING";
        boolean valid = activities.validateFile(fileLocation, fileName);
        if (!valid) {
            currentStatus = "FAILED";
            return new FileProcessingResult(
                    jobId, "FAILED",
                    "Validation failed for " + fileName + " at " + fileLocation);
        }

        // Step 2 — Process
        currentStatus = "PROCESSING";
        String summary = activities.processFile(fileLocation, fileName, jobId);

        currentStatus = "COMPLETED";
        return new FileProcessingResult(jobId, "COMPLETED", summary);
    }

    @Override
    public String getStatus() {
        return currentStatus;
    }
}
