package com.docupload.temporal.activity;

import com.docupload.temporal.workflow.DocumentWorkflowRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Temporal Activity interface — Stage 1: Security Scan &amp; Validation.
 *
 * Activities are the units of work that a Workflow orchestrates. Each activity
 * runs on a Worker (a separate thread pool) and is automatically retried by
 * Temporal on failure according to the {@code RetryOptions} set in the Workflow.
 *
 * <p>Responsibilities of this activity:</p>
 * <ul>
 *   <li>Validate MIME type against an allow-list</li>
 *   <li>Check file size limits</li>
 *   <li>Run a simulated / real virus/malware scan</li>
 *   <li>Return a {@link ScanResult} with a pass/fail verdict and metadata</li>
 * </ul>
 *
 * Implementation class: {@code SecurityScanActivityImpl}
 * (to be created when wiring real workers)
 */
@ActivityInterface
public interface SecurityScanActivity {

    /**
     * Execute the security scan against the document described by {@code request}.
     *
     * @param request  the workflow input (contains storageKey, contentType, etc.)
     * @return         scan verdict and metadata
     */
    @ActivityMethod
    ScanResult scan(DocumentWorkflowRequest request);

    // ── Nested result type ────────────────────────────────────────────────────

    /**
     * Result produced by {@link #scan}.
     * Temporal serialises this as part of the workflow's event history.
     */
    class ScanResult {
        private boolean clean;
        private String  verdict;     // e.g. "CLEAN", "THREAT_DETECTED", "UNSUPPORTED_TYPE"
        private String  mimeType;    // canonicalised MIME type after detection
        private long    durationMs;

        public ScanResult() {}

        public ScanResult(boolean clean, String verdict, String mimeType, long durationMs) {
            this.clean      = clean;
            this.verdict    = verdict;
            this.mimeType   = mimeType;
            this.durationMs = durationMs;
        }

        public boolean isClean()             { return clean; }
        public void    setClean(boolean v)   { this.clean = v; }

        public String  getVerdict()          { return verdict; }
        public void    setVerdict(String v)  { this.verdict = v; }

        public String  getMimeType()         { return mimeType; }
        public void    setMimeType(String v) { this.mimeType = v; }

        public long    getDurationMs()         { return durationMs; }
        public void    setDurationMs(long v)   { this.durationMs = v; }
    }
}
