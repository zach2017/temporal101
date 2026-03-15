package com.docupload.temporal.workflow;

/**
 * Final result returned when a {@link DocumentProcessingWorkflow} run completes.
 *
 * Temporal serialises this as the workflow's return value and stores it in the
 * workflow's completion event. Callers that await the workflow (via
 * {@code WorkflowStub.getResult()}) receive this object directly.
 */
public class DocumentWorkflowResult {

    private String jobId;
    private String originalFileName;
    private long   fileSizeBytes;
    private String contentType;
    private String extractedText;
    private String status;               // SUCCESS | FAILED | CANCELLED
    private String failureReason;        // populated only when status != SUCCESS
    private long   totalProcessingMs;

    // per-stage timings (ms) — useful for diagnostics / UI display
    private long   scanActivityMs;
    private long   extractActivityMs;
    private long   analyzeActivityMs;

    // ── Constructors ──────────────────────────────────────────────────────────

    public DocumentWorkflowResult() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getJobId()                    { return jobId; }
    public void   setJobId(String v)            { this.jobId = v; }

    public String getOriginalFileName()                 { return originalFileName; }
    public void   setOriginalFileName(String v)         { this.originalFileName = v; }

    public long   getFileSizeBytes()              { return fileSizeBytes; }
    public void   setFileSizeBytes(long v)        { this.fileSizeBytes = v; }

    public String getContentType()              { return contentType; }
    public void   setContentType(String v)      { this.contentType = v; }

    public String getExtractedText()            { return extractedText; }
    public void   setExtractedText(String v)    { this.extractedText = v; }

    public String getStatus()                   { return status; }
    public void   setStatus(String v)           { this.status = v; }

    public String getFailureReason()            { return failureReason; }
    public void   setFailureReason(String v)    { this.failureReason = v; }

    public long   getTotalProcessingMs()          { return totalProcessingMs; }
    public void   setTotalProcessingMs(long v)    { this.totalProcessingMs = v; }

    public long   getScanActivityMs()             { return scanActivityMs; }
    public void   setScanActivityMs(long v)       { this.scanActivityMs = v; }

    public long   getExtractActivityMs()          { return extractActivityMs; }
    public void   setExtractActivityMs(long v)    { this.extractActivityMs = v; }

    public long   getAnalyzeActivityMs()          { return analyzeActivityMs; }
    public void   setAnalyzeActivityMs(long v)    { this.analyzeActivityMs = v; }
}
