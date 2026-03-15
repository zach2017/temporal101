package com.temporal.workers.helloworld;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.client.ActivityCompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Long-running activity implementation.
 *
 * Demonstrates:
 *   - Progress heartbeats so Temporal knows the worker is alive.
 *   - Resumption from the last heartbeat if the worker restarts mid-flight.
 *   - Cancellation detection between steps.
 */
public class HelloActivitiesImpl implements HelloActivities {

    private static final Logger logger = LoggerFactory.getLogger(HelloActivitiesImpl.class);

    @Override
    public HelloResult sayHelloLongRunning(HelloInput input) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();

        logger.info("Starting long-running hello for '{}' ({} steps)",
                input.getName(), input.getTotalSteps());

        // ── Resume from last heartbeat if this is a retry ───────
        int startStep = 0;
        Optional<Integer> lastHeartbeat = ctx.getHeartbeatDetails(Integer.class);
        if (lastHeartbeat.isPresent()) {
            startStep = lastHeartbeat.get();
            logger.info("Resuming from step {} (heartbeat recovery)", startStep);
        }

        // ── Simulate long-running work ──────────────────────────
        for (int step = startStep; step < input.getTotalSteps(); step++) {
            // Heartbeat progress — also checks for cancellation
            ctx.heartbeat(step);

            logger.info("[{}] Processing step {}/{} …",
                    input.getName(), step + 1, input.getTotalSteps());

            // Simulate CPU / IO work
            try {
                Thread.sleep((long) (input.getStepDelaySecs() * 1000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw Activity.wrap(e);
            }
        }

        String greeting = String.format(
                "Hello, %s! Completed %d steps of work.",
                input.getName(), input.getTotalSteps()
        );
        logger.info("Finished: {}", greeting);

        return new HelloResult(greeting, input.getTotalSteps());
    }
}
