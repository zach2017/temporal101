package com.temporal.workers.cli;

import com.temporal.workers.config.WorkerConfig;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/**
 * Creates a connected {@link WorkflowClient} from environment-driven config.
 * Shared by every CLI subcommand.
 */
public final class ClientFactory {

    private ClientFactory() {}

    private static WorkflowServiceStubs serviceStubs;

    public static WorkflowClient create() {
        WorkerConfig config = WorkerConfig.getInstance();

        serviceStubs = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(config.getServerUrl())
                        .build()
        );

        return WorkflowClient.newInstance(
                serviceStubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(config.getNamespace())
                        .build()
        );
    }

    public static void shutdown() {
        if (serviceStubs != null) {
            serviceStubs.shutdown();
        }
    }
}
