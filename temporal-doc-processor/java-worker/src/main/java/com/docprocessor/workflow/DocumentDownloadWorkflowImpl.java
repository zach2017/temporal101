package com.docprocessor.workflow;

import com.docprocessor.activity.DocumentActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

public class DocumentDownloadWorkflowImpl implements DocumentDownloadWorkflow {

    private static final Logger log = Workflow.getLogger(DocumentDownloadWorkflowImpl.class);

    private final DocumentActivities activities = Workflow.newActivityStub(
            DocumentActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .build()
    );

    @Override
    public String downloadDocument(String documentId) {
        log.info("Starting download workflow for document: {}", documentId);

        // Step 1: Check if document exists
        boolean exists = activities.documentExists(documentId);
        if (!exists) {
            throw new RuntimeException("Document not found: " + documentId);
        }

        // Step 2: Read and return the text content
        String content = activities.readTextFile(documentId);
        log.info("Download workflow completed for document: {} ({} chars)", documentId, content.length());
        return content;
    }
}
