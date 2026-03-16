package com.example.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Stub interface that mirrors the Python HelloWorldWorkflow.
 * The Java client uses this to build a typed stub — no implementation needed here.
 */
@WorkflowInterface
public interface HelloWorldWorkflow {

    @WorkflowMethod
    String run(String name);
}
