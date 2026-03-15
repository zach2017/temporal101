package com.example.temporaldemo.model;

public class FileResult {
    private String worker;
    private String filename;
    private String detectedType;
    private long fileSizeBytes;
    private String message;

    public FileResult() {
    }

    public FileResult(String worker, String filename, String detectedType, long fileSizeBytes, String message) {
        this.worker = worker;
        this.filename = filename;
        this.detectedType = detectedType;
        this.fileSizeBytes = fileSizeBytes;
        this.message = message;
    }

    public String getWorker() {
        return worker;
    }

    public void setWorker(String worker) {
        this.worker = worker;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getDetectedType() {
        return detectedType;
    }

    public void setDetectedType(String detectedType) {
        this.detectedType = detectedType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
