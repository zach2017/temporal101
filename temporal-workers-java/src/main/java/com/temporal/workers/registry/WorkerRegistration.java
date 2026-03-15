package com.temporal.workers.registry;

import java.util.List;

/**
 * Holds everything needed to start a Temporal worker for a given task queue.
 *
 * @param taskQueue   the Temporal task queue name
 * @param workflows   workflow implementation classes to register
 * @param activities  activity implementation instances to register
 */
public record WorkerRegistration(
        String taskQueue,
        List<Class<?>> workflows,
        List<Object> activities
) {}
