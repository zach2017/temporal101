package demo.temporal.shared.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity interface for file-processing steps.
 *
 * Lives in shared-lib because the Workflow (also in shared-lib) references it
 * by type, and the Worker implements it.
 */
@ActivityInterface
public interface FileProcessingActivities {

    /**
     * Validate that the file exists and is accessible.
     */
    @ActivityMethod
    boolean validateFile(String fileLocation, String fileName);

    /**
     * Process the file (parse, transform, load, etc.).
     *
     * @return summary message describing what was done
     */
    @ActivityMethod
    String processFile(String fileLocation, String fileName, String jobId);
}
