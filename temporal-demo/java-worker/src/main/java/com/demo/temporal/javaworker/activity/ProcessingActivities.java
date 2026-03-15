package com.demo.temporal.javaworker.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ProcessingActivities {

    /** Simulate background work for async workflow (e.g., report generation). */
    @ActivityMethod
    String processAsync(String jobName);

    /** Step 1: Extract data — heartbeats progress. */
    @ActivityMethod
    String extractData(String source);

    /** Step 2: Transform data — heartbeats progress. */
    @ActivityMethod
    String transformData(String rawData);

    /** Step 3: Load data — heartbeats progress. */
    @ActivityMethod
    String loadData(String transformedData);

    /** Step 4: Send notification. */
    @ActivityMethod
    String sendNotification(String summary);
}
