package com.demo.temporal.javaworker.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface JavaHelloWorkflow {

    @WorkflowMethod
    String sayHello(String name);
}
