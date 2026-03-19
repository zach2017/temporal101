package demo.temporal.activity;

import demo.temporal.model.MimeDetectionResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity that detects a file's MIME type and maps it to a
 * {@link demo.temporal.model.DetectedFileType} category.
 *
 * <p>
 * Uses Apache Tika's content-based detection (magic bytes + file name
 * heuristics) rather than trusting the file extension alone.</p>
 */
@ActivityInterface
public interface FileDetectionActivities {

    /**
     * Detect the MIME type of the file at {@code filePath}.
     *
     * @param filePath absolute path to the source file
     * @return detection result containing the raw MIME string and the category
     * enum
     */
    @ActivityMethod
    MimeDetectionResult detectMimeType(String filePath);
}
