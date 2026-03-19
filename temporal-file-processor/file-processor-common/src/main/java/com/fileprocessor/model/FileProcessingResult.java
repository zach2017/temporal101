package com.fileprocessor.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable result payload returned when the File Processing Workflow completes.
 *
 * <h3>Example JSON</h3>
 * <pre>{@code
 * {
 *   "fileName":         "invoice.pdf",
 *   "detectedMimeType": "application/pdf",
 *   "detectedFileType": "PDF",
 *   "textOutputPath":   "/data/outbox/invoice_extracted.txt",
 *   "imageTextOutputs": [
 *     { "imageName": "page_1_img_0.png", "ocrTextPath": "/tmp/.../invoice_images/page_1_img_0.txt" }
 *   ],
 *   "tmpDirectory":     "/tmp/file-processor/invoice",
 *   "totalCharacters":  12340,
 *   "processingTimeMs": 4521,
 *   "success":          true,
 *   "errorMessage":     null,
 *   "metadata":         { "department": "finance" }
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class FileProcessingResult {

    private final String fileName;
    private final String detectedMimeType;
    private final DetectedFileType detectedFileType;
    private final String textOutputPath;
    private final List<ExtractedImageInfo> imageTextOutputs;
    private final String tmpDirectory;
    private final long totalCharacters;
    private final long processingTimeMs;
    private final boolean success;
    private final String errorMessage;
    private final Map<String, String> metadata;
    private final Instant completedAt;

    @JsonCreator
    public FileProcessingResult(
            @JsonProperty("fileName")          String fileName,
            @JsonProperty("detectedMimeType")  String detectedMimeType,
            @JsonProperty("detectedFileType")  DetectedFileType detectedFileType,
            @JsonProperty("textOutputPath")    String textOutputPath,
            @JsonProperty("imageTextOutputs")  List<ExtractedImageInfo> imageTextOutputs,
            @JsonProperty("tmpDirectory")      String tmpDirectory,
            @JsonProperty("totalCharacters")   long totalCharacters,
            @JsonProperty("processingTimeMs")  long processingTimeMs,
            @JsonProperty("success")           boolean success,
            @JsonProperty("errorMessage")      String errorMessage,
            @JsonProperty("metadata")          Map<String, String> metadata,
            @JsonProperty("completedAt")       Instant completedAt) {

        this.fileName          = fileName;
        this.detectedMimeType  = detectedMimeType;
        this.detectedFileType  = detectedFileType;
        this.textOutputPath    = textOutputPath;
        this.imageTextOutputs  = imageTextOutputs;
        this.tmpDirectory      = tmpDirectory;
        this.totalCharacters   = totalCharacters;
        this.processingTimeMs  = processingTimeMs;
        this.success           = success;
        this.errorMessage      = errorMessage;
        this.metadata          = metadata;
        this.completedAt       = completedAt;
    }

    // ── Getters ─────────────────────────────────────────────────────
    public String getFileName()                      { return fileName; }
    public String getDetectedMimeType()              { return detectedMimeType; }
    public DetectedFileType getDetectedFileType()    { return detectedFileType; }
    public String getTextOutputPath()                { return textOutputPath; }
    public List<ExtractedImageInfo> getImageTextOutputs() { return imageTextOutputs; }
    public String getTmpDirectory()                  { return tmpDirectory; }
    public long getTotalCharacters()                 { return totalCharacters; }
    public long getProcessingTimeMs()                { return processingTimeMs; }
    public boolean isSuccess()                       { return success; }
    public String getErrorMessage()                  { return errorMessage; }
    public Map<String, String> getMetadata()         { return metadata; }
    public Instant getCompletedAt()                  { return completedAt; }

    @Override
    public String toString() {
        return "FileProcessingResult{" +
                "fileName='" + fileName + '\'' +
                ", detectedFileType=" + detectedFileType +
                ", success=" + success +
                ", totalCharacters=" + totalCharacters +
                ", processingTimeMs=" + processingTimeMs +
                '}';
    }

    // ── Builder ─────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String fileName;
        private String detectedMimeType;
        private DetectedFileType detectedFileType;
        private String textOutputPath;
        private List<ExtractedImageInfo> imageTextOutputs;
        private String tmpDirectory;
        private long totalCharacters;
        private long processingTimeMs;
        private boolean success;
        private String errorMessage;
        private Map<String, String> metadata;
        private Instant completedAt;

        public Builder fileName(String v)                          { this.fileName = v; return this; }
        public Builder detectedMimeType(String v)                  { this.detectedMimeType = v; return this; }
        public Builder detectedFileType(DetectedFileType v)        { this.detectedFileType = v; return this; }
        public Builder textOutputPath(String v)                    { this.textOutputPath = v; return this; }
        public Builder imageTextOutputs(List<ExtractedImageInfo> v){ this.imageTextOutputs = v; return this; }
        public Builder tmpDirectory(String v)                      { this.tmpDirectory = v; return this; }
        public Builder totalCharacters(long v)                     { this.totalCharacters = v; return this; }
        public Builder processingTimeMs(long v)                    { this.processingTimeMs = v; return this; }
        public Builder success(boolean v)                          { this.success = v; return this; }
        public Builder errorMessage(String v)                      { this.errorMessage = v; return this; }
        public Builder metadata(Map<String, String> v)             { this.metadata = v; return this; }
        public Builder completedAt(Instant v)                      { this.completedAt = v; return this; }

        public FileProcessingResult build() {
            return new FileProcessingResult(
                    fileName, detectedMimeType, detectedFileType,
                    textOutputPath, imageTextOutputs, tmpDirectory,
                    totalCharacters, processingTimeMs, success,
                    errorMessage, metadata,
                    completedAt != null ? completedAt : Instant.now());
        }
    }
}
