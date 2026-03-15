package com.demo.temporal.javaworker.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Async (fire-and-forget) workflow.
 *
 * The caller starts this workflow and gets back a workflowId immediately
 * without waiting for completion. The caller can then poll getStatus()
 * at any time to check progress.
 */
@WorkflowInterface
public interface AsyncProcessingWorkflow {

    @WorkflowMethod
    String runJob(String jobName);

    /** Query the current status without blocking. */
    @QueryMethod
    String getStatus();
}
