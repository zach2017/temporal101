package com.docprocessor.workflow;

import com.docprocessor.model.DocumentResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PdfProcessingWorkflow {

    /**
     * Upload pipeline: receives a document ID (file already saved to temp),
     * converts PDF → text, stores result, returns file reference.
     */
    @WorkflowMethod
    DocumentResult processPdf(String documentId, String originalFileName);
}
