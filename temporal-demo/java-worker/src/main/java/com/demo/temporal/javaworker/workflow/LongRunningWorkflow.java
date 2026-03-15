package com.demo.temporal.javaworker.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Long-running multi-step ETL pipeline workflow.
 *
 * Demonstrates:
 *   - Sequential activity execution (Extract → Transform → Load → Notify)
 *   - Activity heartbeats for liveness detection
 *   - Workflow query for real-time progress tracking
 *   - Automatic retry on failure at any step
 *   - Full audit trail of every step in Temporal event history
 *
 * If the worker crashes mid-pipeline, Temporal replays completed steps
 * from history and resumes from exactly where it left off.
 */
@WorkflowInterface
public interface LongRunningWorkflow {

    @WorkflowMethod
    String runPipeline(String source);

    /** Query current progress: step name, percentage, elapsed time. */
    @QueryMethod
    String getProgress();
}
