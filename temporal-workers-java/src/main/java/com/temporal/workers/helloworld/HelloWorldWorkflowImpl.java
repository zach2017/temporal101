package com.temporal.workers.helloworld;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * Workflow implementation that orchestrates the long-running activity.
 *
 * Timeouts are configured generously for long-running work:
 *   - start-to-close: 30 minutes (total wall clock for the activity)
 *   - heartbeat: 30 seconds (detect dead workers quickly)
 *   - retries: 5 attempts with exponential backoff
 */
public class HelloWorldWorkflowImpl implements HelloWorldWorkflow {

    private static final Logger logger = Workflow.getLogger(HelloWorldWorkflowImpl.class);

    private final HelloActivities activities = Workflow.newActivityStub(
            HelloActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(30))
                    .setHeartbeatTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(
                            RetryOptions.newBuilder()
                                    .setInitialInterval(Duration.ofSeconds(1))
                                    .setBackoffCoefficient(2.0)
                                    .setMaximumInterval(Duration.ofSeconds(30))
                                    .setMaximumAttempts(5)
                                    .build()
                    )
                    .build()
    );

    @Override
    public HelloResult run(String name) {
        logger.info("HelloWorldWorkflow started for '{}'", name);

        HelloResult result = activities.sayHelloLongRunning(new HelloInput(name));

        logger.info("HelloWorldWorkflow completed: {}", result.getGreeting());
        return result;
    }
}
