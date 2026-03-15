package com.demo.temporal.javaworker.workflow;

import com.demo.temporal.javaworker.activity.GreetingActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class JavaHelloWorkflowImpl implements JavaHelloWorkflow {

    // Create an Activity stub with retry policy and timeout
    private final GreetingActivities activities =
            Workflow.newActivityStub(
                    GreetingActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofSeconds(10))
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setMaximumAttempts(3)
                                    .setInitialInterval(Duration.ofSeconds(1))
                                    .setBackoffCoefficient(2.0)
                                    .build())
                            .build());

    @Override
    public String sayHello(String name) {
        // Delegate to the activity — Temporal handles retries & durability
        return activities.composeGreeting(name);
    }
}
