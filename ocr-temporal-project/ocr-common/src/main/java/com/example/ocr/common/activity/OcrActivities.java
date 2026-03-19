package com.example.ocr.common.activity;

import com.example.ocr.common.enums.FileSourceType;
import com.example.ocr.common.model.OcrRequest;
import com.example.ocr.common.model.OcrResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Temporal activity interface for OCR operations.
 *
 * <p>Defines the individual steps of the OCR workflow:
 * file resolution, OCR execution, and cleanup.
 */
@ActivityInterface
public interface OcrActivities {

    /**
     * Resolve the source file to a local path.
     * Downloads from S3/URL or validates local path.
     *
     * @param request the OCR request with file location
     * @return the absolute local file path to the resolved image
     */
    @ActivityMethod
    ResolvedFile resolveFile(OcrRequest request);

    /**
     * Perform OCR on a local image file.
     *
     * @param localFilePath the local path to the image
     * @param request the original OCR request with language/options
     * @return the OCR result
     */
    @ActivityMethod
    OcrResult performOcr(String localFilePath, OcrRequest request);

    /**
     * Clean up temporary files created during processing.
     *
     * @param localFilePath the temporary file to delete
     */
    @ActivityMethod
    void cleanup(String localFilePath);

    /**
     * DTO for the resolved file information.
     */
    class ResolvedFile implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        private String localPath;
        private FileSourceType sourceType;
        private boolean isTemporary;

        public ResolvedFile() {}

        public ResolvedFile(String localPath, FileSourceType sourceType, boolean isTemporary) {
            this.localPath = localPath;
            this.sourceType = sourceType;
            this.isTemporary = isTemporary;
        }

        public String getLocalPath() { return localPath; }
        public void setLocalPath(String localPath) { this.localPath = localPath; }
        public FileSourceType getSourceType() { return sourceType; }
        public void setSourceType(FileSourceType sourceType) { this.sourceType = sourceType; }
        public boolean isTemporary() { return isTemporary; }
        public void setTemporary(boolean temporary) { isTemporary = temporary; }
    }
}
