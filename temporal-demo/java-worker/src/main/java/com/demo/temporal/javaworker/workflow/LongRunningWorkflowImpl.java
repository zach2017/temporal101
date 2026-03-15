package com.demo.temporal.javaworker.workflow;

import com.demo.temporal.javaworker.activity.ProcessingActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.time.Instant;

public class LongRunningWorkflowImpl implements LongRunningWorkflow {

    private String progress = "PENDING|0|Waiting to start";

    private final ProcessingActivities activities =
            Workflow.newActivityStub(
                    ProcessingActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(5))
                            .setHeartbeatTimeout(Duration.ofSeconds(30))
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setMaximumAttempts(3)
                                    .setInitialInterval(Duration.ofSeconds(2))
                                    .setBackoffCoefficient(2.0)
                                    .build())
                            .build());

    @Override
    public String runPipeline(String source) {
        long startTime = Workflow.currentTimeMillis();

        // ── Step 1: Extract ─────────────────────────────────
        progress = "RUNNING|10|Step 1/4: Extracting data from " + source;
        String rawData = activities.extractData(source);
        progress = "RUNNING|25|Step 1/4: Extraction complete (" + elapsed(startTime) + "s)";

        // ── Step 2: Transform ───────────────────────────────
        progress = "RUNNING|35|Step 2/4: Transforming data";
        String transformed = activities.transformData(rawData);
        progress = "RUNNING|55|Step 2/4: Transform complete (" + elapsed(startTime) + "s)";

        // ── Step 3: Load ────────────────────────────────────
        progress = "RUNNING|65|Step 3/4: Loading to warehouse";
        String loaded = activities.loadData(transformed);
        progress = "RUNNING|80|Step 3/4: Load complete (" + elapsed(startTime) + "s)";

        // ── Step 4: Notify ──────────────────────────────────
        progress = "RUNNING|90|Step 4/4: Sending notification";
        String summary = String.format("ETL Pipeline finished: %s → %s in %ss",
                source, loaded, elapsed(startTime));
        activities.sendNotification(summary);

        progress = "COMPLETED|100|Done in " + elapsed(startTime) + "s";
        return summary;
    }

    @Override
    public String getProgress() {
        return progress;
    }

    private long elapsed(long startTime) {
        return (Workflow.currentTimeMillis() - startTime) / 1000;
    }
}
