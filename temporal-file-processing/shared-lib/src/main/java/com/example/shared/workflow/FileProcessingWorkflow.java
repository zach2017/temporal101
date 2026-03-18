package com.example.shared.workflow;

import com.example.shared.model.FileProcessingRequest;
import com.example.shared.model.FileProcessingResult;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Workflow interface for file processing.
 *
 * Lives in shared-lib so that:
 *   • The Client can create a typed WorkflowStub from it
 *   • The Worker can implement it and register the implementation
 */
@WorkflowInterface
public interface FileProcessingWorkflow {

    /**
     * Main entry point — Client calls this; Worker executes it.
     */
    @WorkflowMethod
    FileProcessingResult processFile(FileProcessingRequest request);

    /**
     * Query the current processing status while the Workflow is running.
     */
    @QueryMethod
    String getStatus();
}
