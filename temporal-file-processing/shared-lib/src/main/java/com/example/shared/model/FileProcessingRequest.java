package com.example.shared.model;

import java.util.Objects;

/**
 * Request passed from Client → Workflow → Activities.
 * Serialized by Temporal's default Jackson JSON Payload Converter.
 *
 * Fields:
 *   jobId        — unique identifier for this processing job
 *   fileName     — logical name of the file (e.g. "report-q1.csv")
 *   fileLocation — path or URI where the file can be found
 */
public class FileProcessingRequest {

    private String jobId;
    private String fileName;
    private String fileLocation;

    /** No-arg constructor required by Jackson. */
    public FileProcessingRequest() {}

    public FileProcessingRequest(String jobId, String fileName, String fileLocation) {
        this.jobId         = jobId;
        this.fileName      = fileName;
        this.fileLocation  = fileLocation;
    }

    public String getJobId()        { return jobId; }
    public String getFileName()     { return fileName; }
    public String getFileLocation() { return fileLocation; }

    public void setJobId(String jobId)               { this.jobId = jobId; }
    public void setFileName(String fileName)         { this.fileName = fileName; }
    public void setFileLocation(String fileLocation) { this.fileLocation = fileLocation; }

    @Override
    public String toString() {
        return "FileProcessingRequest{jobId='" + jobId
                + "', fileName='" + fileName
                + "', fileLocation='" + fileLocation + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileProcessingRequest that)) return false;
        return Objects.equals(jobId, that.jobId)
            && Objects.equals(fileName, that.fileName)
            && Objects.equals(fileLocation, that.fileLocation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, fileName, fileLocation);
    }
}
