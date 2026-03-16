package com.temporal.workers.helloworld;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Workflow that orchestrates the long-running Hello World activity.
 */
@WorkflowInterface
public interface HelloWorldWorkflow {

    /**
     * Runs the hello world long-running workflow.
     *
     * @param name the person to greet
     * @return the result from the long-running activity
     */
    @WorkflowMethod
    HelloResult run(String name);
}
