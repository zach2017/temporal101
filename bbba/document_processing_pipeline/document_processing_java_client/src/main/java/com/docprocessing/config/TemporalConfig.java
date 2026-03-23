package com.docprocessing.config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Immutable, ENV-driven configuration for the Temporal client.
 *
 * <p>The {@code taskQueue} must match the Python intake worker's
 * {@code document-intake-queue} so that workflow executions are
 * picked up by the correct worker.
 */
public record TemporalConfig(
        String host,
        String namespace,
        String taskQueue
) {

    public static TemporalConfig load() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        return new TemporalConfig(
                resolve(dotenv, "TEMPORAL_HOST", "host.docker.internal:7233"),
                resolve(dotenv, "TEMPORAL_NAMESPACE", "default"),
                resolve(dotenv, "TEMPORAL_TASK_QUEUE", "document-intake-queue")
        );
    }

    private static String resolve(Dotenv dotenv, String key, String fallback) {
        String value = dotenv.get(key);
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
