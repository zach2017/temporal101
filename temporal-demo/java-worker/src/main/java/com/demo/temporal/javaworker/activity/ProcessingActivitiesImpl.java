package com.demo.temporal.javaworker.activity;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class ProcessingActivitiesImpl implements ProcessingActivities {

    private static final Logger log = LoggerFactory.getLogger(ProcessingActivitiesImpl.class);

    @Override
    public String processAsync(String jobName) {
        log.info("[Async] Starting background job: {}", jobName);
        ActivityExecutionContext ctx = Activity.getExecutionContext();

        // Simulate 5 seconds of work with heartbeat every second
        for (int i = 1; i <= 5; i++) {
            sleep(1000);
            ctx.heartbeat("Processing step " + i + "/5");
            log.info("[Async] Job '{}' progress: {}/5", jobName, i);
        }

        return String.format("Job '%s' completed at %s by Java Worker", jobName, Instant.now());
    }

    @Override
    public String extractData(String source) {
        log.info("[ETL] Extracting data from: {}", source);
        ActivityExecutionContext ctx = Activity.getExecutionContext();

        for (int pct = 0; pct <= 100; pct += 20) {
            sleep(800);
            ctx.heartbeat("Extracting: " + pct + "%");
        }

        String result = "RAW_DATA[source=" + source + ",rows=10000]";
        log.info("[ETL] Extraction complete: {}", result);
        return result;
    }

    @Override
    public String transformData(String rawData) {
        log.info("[ETL] Transforming: {}", rawData);
        ActivityExecutionContext ctx = Activity.getExecutionContext();

        for (int pct = 0; pct <= 100; pct += 25) {
            sleep(600);
            ctx.heartbeat("Transforming: " + pct + "%");
        }

        String result = "TRANSFORMED[" + rawData + ",cleaned=true,normalized=true]";
        log.info("[ETL] Transform complete: {}", result);
        return result;
    }

    @Override
    public String loadData(String transformedData) {
        log.info("[ETL] Loading: {}", transformedData);
        ActivityExecutionContext ctx = Activity.getExecutionContext();

        for (int pct = 0; pct <= 100; pct += 33) {
            sleep(700);
            ctx.heartbeat("Loading: " + Math.min(pct, 100) + "%");
        }

        String result = "LOADED[rows=10000,destination=warehouse]";
        log.info("[ETL] Load complete: {}", result);
        return result;
    }

    @Override
    public String sendNotification(String summary) {
        log.info("[ETL] Sending notification: {}", summary);
        sleep(500);
        return "Notification sent: " + summary;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Activity interrupted", e);
        }
    }
}
