package demo.temporaio.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;

@RestController
public class FileController {

    @PostMapping("/start-workflow")
    public Map<String, String> getFileData() {
        Map<String, String> data = new HashMap<>();

        WorkflowServiceStubs serviceStub = WorkflowServiceStubs.newLocalServiceStubs();

        WorkflowClient client = WorkflowClient.newInstance(serviceStub);

        // Establish the Workflow Options
        WorkflowOptions options = WorkflowOptions
                .newBuilder()
                .setTaskQueue("YourTaskQueue")
                .build();

        YourWorkflow workflowStub = client.newWorkflowStub(YourWorkflow.class, options);

        String results = workflowStub.initiateWorkflow();

        data.put("fileType", "image/png");
        data.put("size", "2.4 MB");

        return data;
    }
}
