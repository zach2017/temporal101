package com.example.temporaldemo.temporal;

import com.example.temporaldemo.model.FileResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface FileInspectorWorkflow {
    @WorkflowMethod
    FileResult inspect(String filename, String targetWorker);
}
