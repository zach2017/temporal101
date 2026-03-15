package com.temporal.workers;

import com.temporal.workers.config.WorkerConfig;
import com.temporal.workers.helloworld.HelloResult;
import com.temporal.workers.helloworld.HelloWorldWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI starter — kicks off a HelloWorldWorkflow execution.
 *
 * Usage:
 *     java -cp app.jar com.temporal.workers.StartHello [NAME]
 */
public class StartHello {

    private static final Logger logger = LoggerFactory.getLogger(StartHello.class);

    public static void main(String[] args) {
        String name = (args.length > 0) ? args[0] : "World";

        WorkerConfig config = WorkerConfig.getInstance();

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(config.getServerUrl())
                        .build()
        );

        WorkflowClient client = WorkflowClient.newInstance(
                service,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(config.getNamespace())
                        .build()
        );

        String workflowId = "hello-world-" + name.toLowerCase().replace(' ', '-');

        HelloWorldWorkflow workflow = client.newWorkflowStub(
                HelloWorldWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue("hello-world-queue")
                        .build()
        );

        logger.info("Starting HelloWorldWorkflow for '{}' …", name);

        HelloResult result = workflow.run(name);

        logger.info("Result: {}", result);
        System.out.printf("✅ %s%n", result.getGreeting());

        System.exit(0);
    }
}
