package com.example.ocr.common.workflow;

import com.example.ocr.common.model.OcrRequest;
import com.example.ocr.common.model.OcrResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Temporal workflow interface for OCR processing.
 *
 * <p>Clients create a workflow stub from this interface to start OCR jobs.
 * The worker provides the implementation.
 */
@WorkflowInterface
public interface OcrWorkflow {

    /**
     * Process an image and extract text using Tesseract OCR.
     *
     * @param request the OCR request containing file location, language, and options
     * @return the OCR result containing extracted text, confidence, and metadata
     */
    @WorkflowMethod
    OcrResult processImage(OcrRequest request);
}
