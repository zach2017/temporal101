package com.temporal.workers.registry;

import com.temporal.workers.helloworld.HelloActivitiesImpl;
import com.temporal.workers.helloworld.HelloWorldWorkflowImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Worker Registry — central place where all workers are registered.
 *
 * <h2>Adding a new worker</h2>
 * <ol>
 *   <li>Create a new package under {@code com.temporal.workers} with your
 *       workflow interface/impl and activity interface/impl.</li>
 *   <li>Add a new {@link WorkerRegistration} entry in
 *       {@link #discoverWorkers()}.</li>
 *   <li>Rebuild and redeploy.</li>
 * </ol>
 */
public final class WorkerRegistry {

    private static final Logger logger = LoggerFactory.getLogger(WorkerRegistry.class);

    private WorkerRegistry() {}

    /**
     * Returns all worker registrations. Add new workers here.
     */
    public static List<WorkerRegistration> discoverWorkers() {
        List<WorkerRegistration> registrations = new ArrayList<>();

        // ──────────────────────────────────────────────
        //  REGISTER WORKERS HERE
        // ──────────────────────────────────────────────

        // 1. Hello World (long-running)
        registrations.add(new WorkerRegistration(
                "hello-world-queue",
                List.of(HelloWorldWorkflowImpl.class),
                List.of(new HelloActivitiesImpl())
        ));

        // 2. Example: Data Pipeline (future worker)
        // registrations.add(new WorkerRegistration(
        //         "data-pipeline-queue",
        //         List.of(DataPipelineWorkflowImpl.class),
        //         List.of(new DataPipelineActivitiesImpl())
        // ));

        // 3. Example: Email Sender (future worker)
        // registrations.add(new WorkerRegistration(
        //         "email-sender-queue",
        //         List.of(EmailSenderWorkflowImpl.class),
        //         List.of(new EmailSenderActivitiesImpl())
        // ));

        // ──────────────────────────────────────────────

        for (WorkerRegistration reg : registrations) {
            logger.info("Registered worker: queue={}, workflows={}, activities={}",
                    reg.taskQueue(),
                    reg.workflows().size(),
                    reg.activities().size());
        }

        return registrations;
    }
}
