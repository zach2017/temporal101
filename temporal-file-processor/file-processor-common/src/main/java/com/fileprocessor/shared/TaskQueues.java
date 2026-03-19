package com.fileprocessor.shared;

/**
 * Shared constants used by both Client and Worker.
 * Centralising Task Queue names here prevents drift between the two sides.
 */
public final class TaskQueues {

    /** The single Task Queue that the file-processing Worker polls. */
    public static final String FILE_PROCESSING_TASK_QUEUE = "FILE_PROCESSING_TASK_QUEUE";

    /** Prefix used when auto-generating Workflow IDs from file names. */
    public static final String WORKFLOW_ID_PREFIX = "file-process-";

    private TaskQueues() { /* utility class */ }
}
