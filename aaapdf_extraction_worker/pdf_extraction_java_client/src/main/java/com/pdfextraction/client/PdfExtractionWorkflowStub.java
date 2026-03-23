package com.pdfextraction.client;

import com.pdfextraction.model.PdfExtractionResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Typed stub interface for the Python {@code PdfExtractionWorkflow}.
 * <p>
 * The {@code @WorkflowMethod} name must match the Python workflow's
 * registered name exactly. The Java SDK uses this interface to generate
 * a type-safe proxy that serialises the call and dispatches it to the
 * Temporal server — the actual execution happens on the Python worker.
 */
@WorkflowInterface
public interface PdfExtractionWorkflowStub {

    /**
     * Start a PDF extraction pipeline.
     *
     * @param fileName     the PDF file name (e.g. "report.pdf")
     * @param fileLocation absolute path on the worker's filesystem
     * @return aggregated extraction result including S3 keys and OCR output
     */
    @WorkflowMethod(name = "PdfExtractionWorkflow")
    PdfExtractionResult extract(String fileName, String fileLocation);
}
