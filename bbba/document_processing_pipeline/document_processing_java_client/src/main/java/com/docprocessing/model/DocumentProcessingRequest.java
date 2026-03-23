package com.docprocessing.model;

/**
 * Value object representing the input to the DocumentIntakeWorkflow.
 *
 * <p>{@code fileType} is <strong>always optional</strong>.  When left
 * blank the Java Tika worker will perform content-based MIME detection
 * as the first step of the workflow pipeline.
 *
 * @param fileName     the document file name (e.g. "report.pdf")
 * @param fileLocation absolute path on the worker's filesystem
 * @param fileType     optional MIME hint (blank = auto-detect via Tika)
 */
public record DocumentProcessingRequest(
        String fileName,
        String fileLocation,
        String fileType
) {

    /** Construct without MIME hint — Tika worker will detect. */
    public DocumentProcessingRequest(String fileName, String fileLocation) {
        this(fileName, fileLocation, "");
    }

    public DocumentProcessingRequest {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (fileLocation == null || fileLocation.isBlank()) {
            throw new IllegalArgumentException("fileLocation must not be blank");
        }
        if (fileType == null) {
            fileType = "";
        }
    }
}
