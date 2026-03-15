package com.docprocessor.workflow;

import com.docprocessor.activity.DocumentActivities;
import com.docprocessor.model.DocumentResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

public class PdfProcessingWorkflowImpl implements PdfProcessingWorkflow {

    private static final Logger log = Workflow.getLogger(PdfProcessingWorkflowImpl.class);

    private final DocumentActivities activities = Workflow.newActivityStub(
            DocumentActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .build()
    );

    @Override
    public DocumentResult processPdf(String documentId, String originalFileName) {
        log.info("Starting PDF processing workflow for: {} ({})", documentId, originalFileName);

        // Step 1: Construct the uploaded PDF path
        String pdfPath = System.getenv("STORAGE_PATH") + "/uploads/" + documentId + "/" + originalFileName;

        // Step 2: Extract text from PDF
        log.info("Step 1/3: Extracting text from PDF...");
        String extractedText = activities.extractTextFromPdf(pdfPath);

        // Step 3: Save extracted text to filesystem
        log.info("Step 2/3: Saving extracted text to filesystem...");
        String textFilePath = activities.saveTextToFile(documentId, originalFileName, extractedText);

        // Step 4: Build and return result
        log.info("Step 3/3: Building result...");
        DocumentResult result = new DocumentResult();
        result.setDocumentId(documentId);
        result.setOriginalFileName(originalFileName);
        result.setTextFilePath(textFilePath);
        result.setStatus("COMPLETED");
        result.setExtractedCharCount(extractedText.length());

        log.info("PDF processing workflow completed for: {}", documentId);
        return result;
    }
}
