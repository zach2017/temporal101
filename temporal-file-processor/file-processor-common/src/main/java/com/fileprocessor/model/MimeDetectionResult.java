package com.fileprocessor.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of the MIME-type detection Activity.
 * Carries both the raw MIME string and the high-level category enum.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MimeDetectionResult {

    private final String mimeType;
    private final DetectedFileType fileType;

    @JsonCreator
    public MimeDetectionResult(
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("fileType") DetectedFileType fileType) {
        this.mimeType = mimeType;
        this.fileType = fileType;
    }

    public String getMimeType()          { return mimeType; }
    public DetectedFileType getFileType() { return fileType; }

    @Override
    public String toString() {
        return "MimeDetectionResult{mimeType='" + mimeType + "', fileType=" + fileType + '}';
    }
}
