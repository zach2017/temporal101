package com.pdfextraction.client;

import com.pdfextraction.config.TemporalConfig;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/**
 * Factory that builds a configured {@link WorkflowClient} from ENV settings.
 * <p>
 * Encapsulates all Temporal connection plumbing so callers only deal with
 * domain-level objects.
 */
public final class TemporalClientFactory {

    private TemporalClientFactory() {}

    /**
     * Create a connected {@link WorkflowClient} using the supplied config.
     */
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
