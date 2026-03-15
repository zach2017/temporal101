package com.demo.temporal.shared;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface JavaHelloWorkflow {

    @WorkflowMethod
    String sayHello(String name);
}
