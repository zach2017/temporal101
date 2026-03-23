package com.docprocessing.client;

import com.docprocessing.config.TemporalConfig;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/**
 * Factory that builds a configured {@link WorkflowClient} from ENV settings.
 */
public final class TemporalClientFactory {

    private TemporalClientFactory() {}

    public static WorkflowClient create(TemporalConfig config) {
        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(config.host())
                .build();

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);

        WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
                .setNamespace(config.namespace())
                .build();

        return WorkflowClient.newInstance(service, clientOptions);
    }
}
