package com.docupload.temporal.workflow;

/**
 * Snapshot of a workflow's current state, returned by the
 * {@link DocumentProcessingWorkflow#getStatus()} {@code @QueryMethod}.
 *
 * The frontend polls GET /api/worker/status/{workflowId} which calls
 * this query under the hood.  All fields must be JSON-serialisable.
 */
public class WorkflowStatusSnapshot {

    /**
     * Current lifecycle state of the workflow run.
     */
    public enum State {
        /** Workflow has been submitted but no activity has started yet. */
        QUEUED,
        /** An activity is actively executing. */
        RUNNING,
        /** All activities completed successfully. */
        COMPLETED,
        /** A non-retryable failure occurred. */
        FAILED,
        /** Cancellation was requested and acknowledged. */
        CANCELLED
    }

    /**
     * Which pipeline stage is currently executing (or last executed).
     *  0 = not started
     *  1 = Security Scan & Validation
     *  2 = OCR & Text Extraction
     *  3 = NLP Analysis & Indexing
     */
    public enum PipelineStage {
        NOT_STARTED,
        SECURITY_SCAN,
        TEXT_EXTRACTION,
        NLP_ANALYSIS,
        DONE
    }

    private String        workflowId;
    private String        jobId;
    private State         state;
    private PipelineStage currentStage;

    /** 0–100 progress percentage computed from completed stages. */
    private int           progressPercent;

    /** Human-readable description of what is happening right now. */
    private String        statusMessage;

    /** ISO-8601 timestamp when the workflow was submitted. */
    private String        startedAt;

    /** ISO-8601 timestamp of the last status update. */
    private String        lastUpdatedAt;

    /** Elapsed wall-clock ms since workflow start (informational). */
    private long          elapsedMs;

    // ── Constructors ─────────────────────────────────────────────────────────

    public WorkflowStatusSnapshot() {}

    public WorkflowStatusSnapshot(String workflowId, String jobId, State state,
                                  PipelineStage stage, int pct, String message) {
        this.workflowId      = workflowId;
        this.jobId           = jobId;
        this.state           = state;
        this.currentStage    = stage;
        this.progressPercent = pct;
        this.statusMessage   = message;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String        getWorkflowId()              { return workflowId; }
    public void          setWorkflowId(String v)      { this.workflowId = v; }

    public String        getJobId()                   { return jobId; }
    public void          setJobId(String v)           { this.jobId = v; }

    public State         getState()                   { return state; }
    public void          setState(State v)            { this.state = v; }

    public PipelineStage getCurrentStage()            { return currentStage; }
    public void          setCurrentStage(PipelineStage v) { this.currentStage = v; }

    public int           getProgressPercent()         { return progressPercent; }
    public void          setProgressPercent(int v)    { this.progressPercent = v; }

    public String        getStatusMessage()           { return statusMessage; }
    public void          setStatusMessage(String v)   { this.statusMessage = v; }

    public String        getStartedAt()               { return startedAt; }
    public void          setStartedAt(String v)       { this.startedAt = v; }

    public String        getLastUpdatedAt()           { return lastUpdatedAt; }
    public void          setLastUpdatedAt(String v)   { this.lastUpdatedAt = v; }

    public long          getElapsedMs()               { return elapsedMs; }
    public void          setElapsedMs(long v)         { this.elapsedMs = v; }
}
