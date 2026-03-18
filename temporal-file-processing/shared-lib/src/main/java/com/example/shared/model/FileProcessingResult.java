package com.example.shared.model;

import java.util.Objects;

/**
 * Result returned by the Workflow Execution back to the Client.
 */
public class FileProcessingResult {

    private String jobId;
    private String status;   // COMPLETED | FAILED
    private String message;

    public FileProcessingResult() {}

    public FileProcessingResult(String jobId, String status, String message) {
        this.jobId   = jobId;
        this.status  = status;
        this.message = message;
    }

    public String getJobId()   { return jobId; }
    public String getStatus()  { return status; }
    public String getMessage() { return message; }

    public void setJobId(String jobId)     { this.jobId = jobId; }
    public void setStatus(String status)   { this.status = status; }
    public void setMessage(String message) { this.message = message; }

    @Override
    public String toString() {
        return "FileProcessingResult{jobId='" + jobId
                + "', status='" + status
                + "', message='" + message + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileProcessingResult that)) return false;
        return Objects.equals(jobId, that.jobId)
            && Objects.equals(status, that.status)
            && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, status, message);
    }
}
