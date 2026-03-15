package com.docupload.model;

public class DocumentProcessingResult {

    private String fileName;
    private String originalFileName;
    private long fileSizeBytes;
    private String contentType;
    private String extractedText;
    private long processingTimeMs;
    private String status;
    private String jobId;

    public DocumentProcessingResult() {}

    public DocumentProcessingResult(String jobId, String fileName, String originalFileName,
                                     long fileSizeBytes, String contentType,
                                     String extractedText, long processingTimeMs, String status) {
        this.jobId = jobId;
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.fileSizeBytes = fileSizeBytes;
        this.contentType = contentType;
        this.extractedText = extractedText;
        this.processingTimeMs = processingTimeMs;
        this.status = status;
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }

    public long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
