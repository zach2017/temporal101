package com.pdfextraction.config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Immutable, ENV-driven configuration for the Temporal client.
 * <p>
 * All external references (host, namespace, queue) are resolved from
 * environment variables or a {@code .env} file — nothing is hard-coded.
 */
public record TemporalConfig(
        String host,
        String namespace,
        String taskQueue
) {

    /**
     * Load configuration from environment / .env file.
     */
    public static TemporalConfig load() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        return new TemporalConfig(
                resolve(dotenv, "TEMPORAL_HOST", "localhost:7233"),
                resolve(dotenv, "TEMPORAL_NAMESPACE", "default"),
                resolve(dotenv, "TEMPORAL_TASK_QUEUE", "pdf-extraction-queue")
        );
    }

    private static String resolve(Dotenv dotenv, String key, String fallback) {
        String value = dotenv.get(key);
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
