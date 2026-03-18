package com.example.shared;

/**
 * Constants shared between Worker and Client.
 * Both reference the same Task Queue so the Temporal Service
 * can route work from the Client to the correct Worker.
 */
public final class SharedConstants {

    private SharedConstants() {}

    /** Task Queue that the Worker polls and the Client submits to. */
    public static final String TASK_QUEUE = "FILE_PROCESSING_TASK_QUEUE";
}
