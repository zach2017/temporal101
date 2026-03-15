package com.docprocessor.model;

import java.io.Serializable;

public class DocumentResult implements Serializable {
    private String documentId;
    private String originalFileName;
    private String textFilePath;
    private String status;
    private long extractedCharCount;

    public DocumentResult() {}

    public DocumentResult(String documentId, String originalFileName, String textFilePath, String status, long extractedCharCount) {
        this.documentId = documentId;
        this.originalFileName = originalFileName;
        this.textFilePath = textFilePath;
        this.status = status;
        this.extractedCharCount = extractedCharCount;
    }

    // Getters and setters
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getTextFilePath() { return textFilePath; }
    public void setTextFilePath(String textFilePath) { this.textFilePath = textFilePath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getExtractedCharCount() { return extractedCharCount; }
    public void setExtractedCharCount(long extractedCharCount) { this.extractedCharCount = extractedCharCount; }
}
