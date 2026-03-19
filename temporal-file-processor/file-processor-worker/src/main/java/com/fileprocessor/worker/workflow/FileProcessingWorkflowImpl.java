package com.fileprocessor.worker.workflow;

import com.fileprocessor.activity.FileDetectionActivities;
import com.fileprocessor.activity.FileStorageActivities;
import com.fileprocessor.activity.OcrActivities;
import com.fileprocessor.activity.TextExtractionActivities;
import com.fileprocessor.model.*;
import com.fileprocessor.workflow.FileProcessingWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Temporal Workflow implementation that orchestrates the full file-processing
 * pipeline.
 *
 * <h3>Pipeline</h3>
 * <pre>
 *  ┌───────────────┐     ┌─────────────────┐     ┌───────────────────────┐
 *  │ Detect MIME    │────▶│ Route by type    │────▶│ Extract / OCR / Store │
 *  └───────────────┘     └─────────────────┘     └───────────────────────┘
 *
 *  IMAGE  ──▶ OCR(image) ──▶ write .txt
 *  PDF    ──▶ extractText(pdf) + extractImages(pdf) ──▶ OCR(each image) ──▶ merge ──▶ write .txt
 *  WORD   ──▶ extractOffice ──▶ write .txt
 *  EXCEL  ──▶ extractOffice ──▶ write .txt
 *  PPTX   ──▶ extractOffice ──▶ write .txt
 *  TEXT   ──▶ copy text ──▶ write .txt
 * </pre>
 *
 * <p><b>Determinism:</b> this class follows Temporal's determinism rules —
 * no direct I/O, no {@code System.currentTimeMillis()}, no random.  All
 * side-effecting work is delegated to Activities.</p>
 */
public class FileProcessingWorkflowImpl implements FileProcessingWorkflow {

    private static final Logger log = Workflow.getLogger(FileProcessingWorkflowImpl.class);

    private static final String TMP_BASE = "/tmp/file-processor";

    // ── Current status (queryable) ───────────────────────────────────
    private String status = "INITIALISING";
    private boolean cancelRequested = false;

    // ── Activity stubs ───────────────────────────────────────────────

    /** Short-lived Activities: MIME detection, plain-text reads. */
    private final FileDetectionActivities detectionActivities =
            Workflow.newActivityStub(FileDetectionActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofSeconds(30))
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setMaximumAttempts(3)
                                    .build())
                            .build());

    /** Medium-lived Activities: text extraction from PDFs & Office docs. */
    private final TextExtractionActivities textActivities =
            Workflow.newActivityStub(TextExtractionActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(10))
                            .setHeartbeatTimeout(Duration.ofMinutes(2))
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setMaximumAttempts(3)
                                    .setInitialInterval(Duration.ofSeconds(2))
                                    .setBackoffCoefficient(2.0)
                                    .build())
                            .build());

    /** Long-lived Activities: Tesseract OCR (can be slow on large images). */
    private final OcrActivities ocrActivities =
            Workflow.newActivityStub(OcrActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(15))
                            .setHeartbeatTimeout(Duration.ofMinutes(3))
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setMaximumAttempts(3)
                                    .setInitialInterval(Duration.ofSeconds(5))
                                    .setBackoffCoefficient(2.0)
                                    .build())
                            .build());

    /** File I/O Activities: directory creation, merge, copy. */
    private final FileStorageActivities storageActivities =
            Workflow.newActivityStub(FileStorageActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(5))
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setMaximumAttempts(3)
                                    .build())
                            .build());

    // ═════════════════════════════════════════════════════════════════
    //  Main Workflow Method
    // ═════════════════════════════════════════════════════════════════

    @Override
    public FileProcessingResult processFile(FileProcessingRequest request) {
        log.info("Starting file processing workflow for: {}", request);
        long startMs = Workflow.currentTimeMillis();

        try {
            // ── 1. Create tmp directories ────────────────────────
            status = "CREATING_TMP_DIRS";
            String baseName = stripExtension(request.getFileName());
            String tmpDir = storageActivities.createTmpDirectories(TMP_BASE, baseName);

            if (cancelRequested) return buildCancelled(request, startMs, tmpDir);

            // ── 2. Detect MIME type ──────────────────────────────
            status = "DETECTING_MIME";
            String sourcePath = request.getFullSourcePath();
            MimeDetectionResult mimeResult = detectionActivities.detectMimeType(sourcePath);
            log.info("MIME detection result: {}", mimeResult);

            if (cancelRequested) return buildCancelled(request, startMs, tmpDir);

            // ── 3. Route to extraction strategy ──────────────────
            String textOutputInTmp = tmpDir + "/" + baseName + "_extracted.txt";
            String imageDir = tmpDir + "/" + baseName + "_images";
            List<ExtractedImageInfo> imageResults = new ArrayList<>();

            switch (mimeResult.getFileType()) {
                case IMAGE:
                    status = "RUNNING_OCR";
                    ExtractedImageInfo ocrResult = ocrActivities.ocrImage(
                            sourcePath, textOutputInTmp);
                    imageResults.add(ocrResult);
                    break;

                case PDF:
                    status = "EXTRACTING_PDF_TEXT";
                    String pdfTextPath = tmpDir + "/" + baseName + "_pdf_text.txt";
                    textActivities.extractPdfText(sourcePath, pdfTextPath);

                    if (cancelRequested) return buildCancelled(request, startMs, tmpDir);

                    // Extract images from PDF
                    status = "EXTRACTING_PDF_IMAGES";
                    List<String> extractedImages =
                            textActivities.extractPdfImages(sourcePath, imageDir);

                    // OCR each extracted image
                    List<String> allTextPaths = new ArrayList<>();
                    allTextPaths.add(pdfTextPath);

                    if (!extractedImages.isEmpty()) {
                        status = "RUNNING_OCR_ON_PDF_IMAGES";
                        for (int i = 0; i < extractedImages.size(); i++) {
                            if (cancelRequested) break;

                            String imgPath = extractedImages.get(i);
                            String imgBaseName = Path_getFileName(imgPath);
                            String imgTextPath = imageDir + "/" +
                                    stripExtension(imgBaseName) + ".txt";

                            ExtractedImageInfo imgOcr =
                                    ocrActivities.ocrImage(imgPath, imgTextPath);
                            imageResults.add(imgOcr);
                            allTextPaths.add(imgTextPath);

                            log.info("OCR'd image {}/{}: {}", i + 1,
                                    extractedImages.size(), imgBaseName);
                        }
                    }

                    // Merge PDF text + all image OCR texts
                    status = "MERGING_TEXT";
                    storageActivities.mergeTextFiles(allTextPaths, textOutputInTmp);
                    break;

                case WORD_DOCUMENT:
                case SPREADSHEET:
                case PRESENTATION:
                    status = "EXTRACTING_OFFICE_TEXT";
                    textActivities.extractOfficeText(sourcePath, textOutputInTmp);
                    break;

                case PLAIN_TEXT:
                    status = "EXTRACTING_TEXT";
                    textActivities.extractPlainText(sourcePath, textOutputInTmp);
                    break;

                case UNSUPPORTED:
                    log.warn("Unsupported file type: {} ({})",
                            mimeResult.getFileType(), mimeResult.getMimeType());
                    return FileProcessingResult.builder()
                            .fileName(request.getFileName())
                            .detectedMimeType(mimeResult.getMimeType())
                            .detectedFileType(mimeResult.getFileType())
                            .tmpDirectory(tmpDir)
                            .success(false)
                            .errorMessage("Unsupported file type: " + mimeResult.getMimeType())
                            .metadata(request.getMetadata())
                            .processingTimeMs(Workflow.currentTimeMillis() - startMs)
                            .build();
            }

            if (cancelRequested) return buildCancelled(request, startMs, tmpDir);

            // ── 4. Copy to final output location ─────────────────
            status = "COPYING_TO_OUTPUT";
            String outputFileName = baseName + "_extracted.txt";
            String finalOutputPath = storageActivities.copyToOutput(
                    textOutputInTmp, request.getOutputLocation(), outputFileName);

            // ── 5. Build success result ──────────────────────────
            status = "COMPLETED";
            long totalChars = countCharsFromImageResults(imageResults);

            FileProcessingResult result = FileProcessingResult.builder()
                    .fileName(request.getFileName())
                    .detectedMimeType(mimeResult.getMimeType())
                    .detectedFileType(mimeResult.getFileType())
                    .textOutputPath(finalOutputPath)
                    .imageTextOutputs(imageResults)
                    .tmpDirectory(tmpDir)
                    .totalCharacters(totalChars)
                    .processingTimeMs(Workflow.currentTimeMillis() - startMs)
                    .success(true)
                    .metadata(request.getMetadata())
                    .build();

            log.info("Workflow completed successfully: {}", result);
            return result;

        } catch (Exception e) {
            status = "FAILED";
            log.error("Workflow failed for {}", request.getFileName(), e);
            return FileProcessingResult.builder()
                    .fileName(request.getFileName())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .metadata(request.getMetadata())
                    .processingTimeMs(Workflow.currentTimeMillis() - startMs)
                    .build();
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Query & Signal
    // ═════════════════════════════════════════════════════════════════

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public void cancelProcessing() {
        log.info("Cancel signal received");
        cancelRequested = true;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Helpers (pure functions — safe in Workflow code)
    // ═════════════════════════════════════════════════════════════════

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** Workflow code cannot use {@code java.nio.file.Path}, so we do it manually. */
    private static String Path_getFileName(String path) {
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return sep >= 0 ? path.substring(sep + 1) : path;
    }

    private static long countCharsFromImageResults(List<ExtractedImageInfo> results) {
        return results.stream().mapToLong(ExtractedImageInfo::getCharacterCount).sum();
    }

    private FileProcessingResult buildCancelled(
            FileProcessingRequest request, long startMs, String tmpDir) {
        status = "CANCELLED";
        return FileProcessingResult.builder()
                .fileName(request.getFileName())
                .tmpDirectory(tmpDir)
                .success(false)
                .errorMessage("Processing cancelled by signal")
                .metadata(request.getMetadata())
                .processingTimeMs(Workflow.currentTimeMillis() - startMs)
                .build();
    }
}
