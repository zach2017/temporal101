package com.pdfworker.workflow;

import com.pdfworker.model.PdfProcessingRequest;
import com.pdfworker.model.PdfProcessingResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Workflow stub interface.
 * The workflow name and task queue MUST match the Python worker constants.
 */
@WorkflowInterface
public interface PdfToTextWorkflow {

    /** Must match Python {@code WORKFLOW_NAME = "PdfToTextWorkflow"}. */
    String WORKFLOW_NAME = "PdfToTextWorkflow";

    /** Must match Python {@code TASK_QUEUE = "pdf-to-text-queue"}. */
    String TASK_QUEUE = "pdf-to-text-queue";

    @WorkflowMethod(name = WORKFLOW_NAME)
    PdfProcessingResult processPdf(PdfProcessingRequest request);
}
