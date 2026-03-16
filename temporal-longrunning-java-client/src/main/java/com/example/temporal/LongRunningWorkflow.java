package com.example.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.Map;

/**
 * Stub interface mirroring the Python LongRunningWorkflow.
 * The payload matches: {"job_id": "...", "steps": N}
 * The result is the final notification message string.
 */
@WorkflowInterface
public interface LongRunningWorkflow {

    @WorkflowMethod
    String run(Map<String, Object> payload);
}
