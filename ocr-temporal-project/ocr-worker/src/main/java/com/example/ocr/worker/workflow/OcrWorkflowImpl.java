package com.example.ocr.worker.workflow;

import com.example.ocr.common.activity.OcrActivities;
import com.example.ocr.common.activity.OcrActivities.ResolvedFile;
import com.example.ocr.common.constants.OcrConstants;
import com.example.ocr.common.model.OcrRequest;
import com.example.ocr.common.model.OcrResult;
import com.example.ocr.common.workflow.OcrWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * Temporal workflow implementation for OCR processing.
 *
 * <p>Orchestrates the three activities: file resolution, OCR execution, and cleanup.
 * Each activity has its own timeout and retry configuration.
 */
public class OcrWorkflowImpl implements OcrWorkflow {

    private static final Logger log = Workflow.getLogger(OcrWorkflowImpl.class);

    // Activity stub for file resolution (may need longer timeout for S3/URL downloads)
    private final OcrActivities fileActivities = Workflow.newActivityStub(
            OcrActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(120))
                    .setHeartbeatTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(5))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    // Activity stub for OCR processing (CPU-intensive, longer timeout)
    private final OcrActivities ocrActivities = Workflow.newActivityStub(
            OcrActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setHeartbeatTimeout(Duration.ofSeconds(60))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(2)
                            .setInitialInterval(Duration.ofSeconds(10))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    // Activity stub for cleanup (fast, best-effort)
    private final OcrActivities cleanupActivities = Workflow.newActivityStub(
            OcrActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(2)
                            .setInitialInterval(Duration.ofSeconds(2))
                            .build())
                    .build());

    @Override
    public OcrResult processImage(OcrRequest request) {
        log.info("Starting OCR workflow for file: {} at location: {}",
                request.getFileName(), request.getFileLocation());

        // Validate request
        if (request.getFileName() == null || request.getFileName().isBlank()) {
            throw new IllegalArgumentException("fileName is required");
        }
        if (request.getFileLocation() == null || request.getFileLocation().isBlank()) {
            throw new IllegalArgumentException("fileLocation is required");
        }

        // Default language to English if not specified
        if (request.getLanguage() == null || request.getLanguage().isBlank()) {
            request.setLanguage(OcrConstants.DEFAULT_LANGUAGE);
        }

        ResolvedFile resolvedFile = null;
        try {
            // Step 1: Resolve the file to a local path
            resolvedFile = fileActivities.resolveFile(request);
            log.info("File resolved: {} -> {}", request.getFileLocation(), resolvedFile.getLocalPath());

            // Step 2: Perform OCR
            OcrResult result = ocrActivities.performOcr(resolvedFile.getLocalPath(), request);
            result.setSourceFileType(resolvedFile.getSourceType());

            log.info("OCR workflow completed: textFound={}, confidence={:.1f}%",
                    result.isTextFound(), result.getMeanConfidence());

            return result;

        } finally {
            // Step 3: Cleanup temporary files (only if file was downloaded/temporary)
            if (resolvedFile != null && resolvedFile.isTemporary()) {
                try {
                    cleanupActivities.cleanup(resolvedFile.getLocalPath());
                } catch (Exception e) {
                    log.warn("Cleanup failed for {}: {}", resolvedFile.getLocalPath(), e.getMessage());
                }
            }
        }
    }
}
