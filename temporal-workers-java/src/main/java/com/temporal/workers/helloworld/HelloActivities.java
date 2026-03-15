package com.temporal.workers.helloworld;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity interface for the Hello World long-running task.
 */
@ActivityInterface
public interface HelloActivities {

    /**
     * A long-running activity that greets someone while performing
     * multi-step processing. Each step heartbeats its progress so
     * Temporal can track liveness and enable resumption on failure.
     */
    @ActivityMethod
    HelloResult sayHelloLongRunning(HelloInput input);
}
