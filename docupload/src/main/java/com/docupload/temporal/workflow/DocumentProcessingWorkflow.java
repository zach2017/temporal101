package com.docupload.temporal.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Temporal Workflow interface for the document processing pipeline.
 *
 * A Workflow in Temporal is a durable, fault-tolerant orchestrator. It defines
 * the high-level sequence of steps (Activities) that must be executed to process
 * a document. Temporal persists the workflow's event history so that if the
 * server restarts mid-way, the workflow resumes exactly where it left off.
 *
 * Pipeline stages (each is a separate Activity):
 *   1. validateAndScan     — virus / MIME-type check
 *   2. extractText         — OCR / parser
 *   3. analyzeAndIndex     — NLP enrichment, metadata extraction
 *
 * Implementation class: {@code DocumentProcessingWorkflowImpl}
 * (to be created when wiring real workers)
 */
@WorkflowInterface
public interface DocumentProcessingWorkflow {

    /**
     * Main workflow entry point. Accepts metadata about the uploaded file
     * (the bytes themselves live in object storage; only the reference is passed).
     *
     * @param request  describes the uploaded document (name, size, storageKey, etc.)
     * @return         the final extraction result once the full pipeline completes
     */
    @WorkflowMethod
    DocumentWorkflowResult processDocument(DocumentWorkflowRequest request);

    /**
     * Query — callable at any time to get the current pipeline stage and
     * progress percentage WITHOUT advancing the workflow.
     *
     * The HTML frontend polls this via GET /api/worker/status/{workflowId}.
     */
    @QueryMethod
    WorkflowStatusSnapshot getStatus();

    /**
     * Signal — allows an external caller (e.g. an admin endpoint) to request
     * a graceful cancellation of an in-progress workflow. The implementation
     * is responsible for honouring it between activity calls.
     */
    @SignalMethod
    void requestCancellation(String reason);
}
