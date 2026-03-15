package com.demo.temporal.javaworker.workflow;

import com.demo.temporal.javaworker.activity.ProcessingActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class AsyncProcessingWorkflowImpl implements AsyncProcessingWorkflow {

    private String status = "QUEUED";

    private final ProcessingActivities activities =
            Workflow.newActivityStub(
                    ProcessingActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofSeconds(30))
                            .setHeartbeatTimeout(Duration.ofSeconds(10))
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setMaximumAttempts(3)
                                    .build())
                            .build());

    @Override
    public String runJob(String jobName) {
        status = "RUNNING";
        String result = activities.processAsync(jobName);
        status = "COMPLETED";
        return result;
    }

    @Override
    public String getStatus() {
        return status;
    }
}
