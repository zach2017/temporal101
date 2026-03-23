package com.docprocessing.client;

import com.docprocessing.model.DocumentProcessingResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Typed stub interface for the Python {@code DocumentIntakeWorkflow}.
 *
 * <p>The {@code @WorkflowMethod} name <strong>must</strong> match the Python
 * workflow's registered name exactly:
 * <pre>
 *   @workflow.defn(name="DocumentIntakeWorkflow")
 * </pre>
 *
 * <p>The Java SDK serialises the three arguments as a JSON array and
 * publishes them to the {@code document-intake-queue} task queue.
 * The Python intake worker picks up the task and executes the workflow.
 *
 * <p><strong>Contract:</strong>
 * <ul>
 *   <li>{@code fileName}     — document file name (e.g. "report.pdf")</li>
 *   <li>{@code fileLocation} — absolute path on the worker's filesystem</li>
 *   <li>{@code fileType}     — MIME type hint (empty string = auto-detect)</li>
 * </ul>
 */
@WorkflowInterface
public interface DocumentIntakeWorkflowStub {

    @WorkflowMethod(name = "DocumentIntakeWorkflow")
    DocumentProcessingResult process(String fileName, String fileLocation, String fileType);
}
