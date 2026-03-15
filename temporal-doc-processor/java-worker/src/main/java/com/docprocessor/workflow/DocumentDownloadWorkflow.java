package com.docprocessor.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface DocumentDownloadWorkflow {

    /**
     * Download workflow: given a document ID, locates the extracted text file
     * and returns its content.
     */
    @WorkflowMethod
    String downloadDocument(String documentId);
}
