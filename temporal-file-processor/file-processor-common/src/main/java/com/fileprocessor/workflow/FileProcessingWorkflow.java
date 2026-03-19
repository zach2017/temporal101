package com.fileprocessor.workflow;

import com.fileprocessor.model.FileProcessingRequest;
import com.fileprocessor.model.FileProcessingResult;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Temporal Workflow interface for the file-processing pipeline.
 *
 * <p>This interface lives in the <b>common</b> module so that both the
 * Worker (which provides the implementation) and the Client (which creates
 * typed stubs) share the same contract.</p>
 *
 * <h3>Pipeline stages (executed as Activities)</h3>
 * <ol>
 *   <li>Detect MIME type → categorise as IMAGE / PDF / WORD / TEXT / …</li>
 *   <li>Route to the correct extraction strategy</li>
 *   <li>For PDFs: extract text <b>and</b> extract embedded images → OCR each</li>
 *   <li>For Images: OCR the file directly via Tesseract</li>
 *   <li>For documents/text: extract text via Tika / POI</li>
 *   <li>Persist all extracted text to the output location + tmp directory</li>
 * </ol>
 */
@WorkflowInterface
public interface FileProcessingWorkflow {

    /**
     * Main entry point. Blocks until the entire pipeline completes.
     *
     * @param request  file coordinates + optional metadata
     * @return result  summary including output paths, character counts, timing
     */
    @WorkflowMethod
    FileProcessingResult processFile(FileProcessingRequest request);

    /**
     * Query: returns a human-readable status string such as
     * "DETECTING_MIME", "EXTRACTING_TEXT", "RUNNING_OCR", "COMPLETED".
     */
    @QueryMethod
    String getStatus();

    /**
     * Signal: allows an external caller to request cancellation.
     * The Workflow will finish its current Activity and then stop.
     */
    @SignalMethod
    void cancelProcessing();
}
