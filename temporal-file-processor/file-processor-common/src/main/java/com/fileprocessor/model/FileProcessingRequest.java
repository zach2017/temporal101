package com.fileprocessor.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable input payload for the File Processing Workflow.
 *
 * <p>Temporal serialises this via its default Jackson-based DataConverter,
 * so we keep it Jackson-friendly with explicit {@code @JsonCreator}.</p>
 *
 * <h3>Example JSON</h3>
 * <pre>{@code
 * {
 *   "fileName":       "invoice.pdf",
 *   "fileLocation":   "/data/inbox/invoice.pdf",
 *   "outputLocation": "/data/outbox",
 *   "metadata": {
 *     "department": "finance",
 *     "uploadedBy": "jsmith"
 *   }
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class FileProcessingRequest {

    private final String fileName;
    private final String fileLocation;
    private final String outputLocation;
    private final Map<String, String> metadata;

    @JsonCreator
    public FileProcessingRequest(
            @JsonProperty("fileName")       String fileName,
            @JsonProperty("fileLocation")   String fileLocation,
            @JsonProperty("outputLocation") String outputLocation,
            @JsonProperty("metadata")       Map<String, String> metadata) {

        this.fileName       = Objects.requireNonNull(fileName, "fileName is required");
        this.fileLocation   = Objects.requireNonNull(fileLocation, "fileLocation is required");
        this.outputLocation = Objects.requireNonNull(outputLocation, "outputLocation is required");
        this.metadata       = metadata;   // nullable — purely optional
    }

    public String getFileName()              { return fileName; }
    public String getFileLocation()          { return fileLocation; }
    public String getOutputLocation()        { return outputLocation; }
    public Map<String, String> getMetadata() { return metadata; }

    /** Convenience: full path = fileLocation (if it already includes the name). */
    public String getFullSourcePath() {
        if (fileLocation.endsWith(fileName)) {
            return fileLocation;
        }
        return fileLocation.endsWith("/")
                ? fileLocation + fileName
                : fileLocation + "/" + fileName;
    }

    @Override
    public String toString() {
        return "FileProcessingRequest{" +
                "fileName='" + fileName + '\'' +
                ", fileLocation='" + fileLocation + '\'' +
                ", outputLocation='" + outputLocation + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}
