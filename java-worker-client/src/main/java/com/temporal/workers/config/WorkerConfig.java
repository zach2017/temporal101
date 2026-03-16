package com.temporal.workers.config;

/**
 * Centralized configuration for Temporal workers.
 * All settings are driven by environment variables with sensible defaults.
 */
public class WorkerConfig {

    // ── Temporal Server ─────────────────────────
    private final String temporalHost;
    private final int temporalPort;
    private final String namespace;

    // ── Worker Tuning ───────────────────────────
    private final int maxConcurrentActivities;
    private final int maxConcurrentWorkflows;
    private final String logLevel;

    private static final WorkerConfig INSTANCE = new WorkerConfig();

    private WorkerConfig() {
        this.temporalHost           = env("TEMPORAL_HOST", "localhost");
        this.temporalPort           = Integer.parseInt(env("TEMPORAL_PORT", "7233"));
        this.namespace              = env("TEMPORAL_NAMESPACE", "default");
        this.maxConcurrentActivities = Integer.parseInt(env("WORKER_MAX_CONCURRENT_ACTIVITIES", "10"));
        this.maxConcurrentWorkflows  = Integer.parseInt(env("WORKER_MAX_CONCURRENT_WORKFLOWS", "10"));
        this.logLevel               = env("WORKER_LOG_LEVEL", "INFO");
    }

    public static WorkerConfig getInstance() {
        return INSTANCE;
    }

    public String getServerUrl() {
        return temporalHost + ":" + temporalPort;
    }

    public String getTemporalHost()          { return temporalHost; }
    public int    getTemporalPort()          { return temporalPort; }
    public String getNamespace()             { return namespace; }
    public int    getMaxConcurrentActivities() { return maxConcurrentActivities; }
    public int    getMaxConcurrentWorkflows()  { return maxConcurrentWorkflows; }
    public String getLogLevel()              { return logLevel; }

    @Override
    public String toString() {
        return String.format(
            "WorkerConfig{server=%s, namespace=%s, activities=%d, workflows=%d, log=%s}",
            getServerUrl(), namespace, maxConcurrentActivities, maxConcurrentWorkflows, logLevel
        );
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
