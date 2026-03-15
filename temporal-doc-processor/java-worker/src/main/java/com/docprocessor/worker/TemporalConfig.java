package com.docprocessor.worker;

import com.docprocessor.activity.DocumentActivitiesImpl;
import com.docprocessor.workflow.DocumentDownloadWorkflowImpl;
import com.docprocessor.workflow.PdfProcessingWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    private static final Logger log = LoggerFactory.getLogger(TemporalConfig.class);
    public static final String TASK_QUEUE = "DOC_PROCESSING_QUEUE";

    @Value("${temporal.address}")
    private String temporalAddress;

    @Autowired
    private DocumentActivitiesImpl documentActivities;

    private WorkerFactory workerFactory;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        log.info("Connecting to Temporal server at: {}", temporalAddress);
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(temporalAddress)
                        .build()
        );
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs);
    }

    @PostConstruct
    public void startWorker() {
        WorkflowServiceStubs serviceStubs = workflowServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(serviceStubs);
        workerFactory = WorkerFactory.newInstance(client);

        Worker worker = workerFactory.newWorker(TASK_QUEUE);

        // Register workflows
        worker.registerWorkflowImplementationTypes(
                PdfProcessingWorkflowImpl.class,
                DocumentDownloadWorkflowImpl.class
        );

        // Register activities (Spring-managed bean with injected dependencies)
        worker.registerActivitiesImplementations(documentActivities);

        workerFactory.start();
        log.info("Temporal worker started on task queue: {}", TASK_QUEUE);
    }

    @PreDestroy
    public void stopWorker() {
        if (workerFactory != null) {
            workerFactory.shutdown();
            log.info("Temporal worker stopped");
        }
    }
}
