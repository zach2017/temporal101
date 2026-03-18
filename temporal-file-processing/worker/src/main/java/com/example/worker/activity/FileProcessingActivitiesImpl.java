package com.example.worker.activity;

import com.example.shared.activity.FileProcessingActivities;
import io.temporal.activity.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activity implementation — contains the real business logic.
 * Only the Worker project needs this class.
 */
public class FileProcessingActivitiesImpl implements FileProcessingActivities {

    private static final Logger log =
            LoggerFactory.getLogger(FileProcessingActivitiesImpl.class);

    @Override
    public boolean validateFile(String fileLocation, String fileName) {
        log.info("[validateFile] Checking '{}' at '{}'", fileName, fileLocation);

        // Real app: check file exists, verify format, permissions, etc.
        if (fileName == null || fileName.isBlank()
                || fileLocation == null || fileLocation.isBlank()) {
            log.warn("[validateFile] INVALID — empty fileName or fileLocation");
            return false;
        }

        log.info("[validateFile] VALID");
        return true;
    }

    @Override
    public String processFile(String fileLocation, String fileName, String jobId) {
        log.info("[processFile] jobId={} — processing '{}' at '{}'",
                jobId, fileName, fileLocation);

        // Simulate work (real app: parse CSV, transform data, call API, etc.)
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Activity.wrap(e);
        }

        String summary = String.format(
                "Job %s: successfully processed '%s' from '%s'",
                jobId, fileName, fileLocation);
        log.info("[processFile] {}", summary);
        return summary;
    }
}
