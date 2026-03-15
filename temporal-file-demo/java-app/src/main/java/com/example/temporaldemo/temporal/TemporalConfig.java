package com.example.temporaldemo.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class TemporalConfig {

    @Bean
    public WorkflowServiceStubs workflowServiceStubs(AppProperties properties) {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(properties.getTemporal().getAddress())
                        .build()
        );
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs, AppProperties properties) {
        return WorkflowClient.newInstance(stubs,
                io.temporal.client.WorkflowClientOptions.newBuilder()
                        .setNamespace(properties.getTemporal().getNamespace())
                        .build());
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public WorkerFactory workerFactory(WorkflowClient client, AppProperties properties) {
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker workflowWorker = factory.newWorker(properties.getTemporal().getWorkflowTaskQueue());
        workflowWorker.registerWorkflowImplementationTypes(FileInspectorWorkflowImpl.class);

        Worker javaActivityWorker = factory.newWorker(properties.getTemporal().getJavaActivityTaskQueue());
        javaActivityWorker.registerActivitiesImplementations(new JavaFileInspectorActivities(properties));

        return factory;
    }
}
