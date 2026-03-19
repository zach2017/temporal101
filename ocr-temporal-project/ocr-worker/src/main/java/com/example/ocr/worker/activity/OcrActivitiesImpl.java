package com.example.ocr.worker.activity;

import com.example.ocr.common.activity.OcrActivities;
import com.example.ocr.common.model.OcrRequest;
import com.example.ocr.common.model.OcrResult;
import com.example.ocr.worker.resolver.FileResolver;
import com.example.ocr.worker.service.TesseractOcrService;
import io.temporal.activity.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implementation of OCR activities.
 * Handles file resolution, OCR execution, and cleanup.
 */
public class OcrActivitiesImpl implements OcrActivities {

    private static final Logger log = LoggerFactory.getLogger(OcrActivitiesImpl.class);

    private final FileResolver fileResolver;
    private final TesseractOcrService ocrService;

    public OcrActivitiesImpl(FileResolver fileResolver, TesseractOcrService ocrService) {
        this.fileResolver = fileResolver;
        this.ocrService = ocrService;
    }

    @Override
    public ResolvedFile resolveFile(OcrRequest request) {
        log.info("Resolving file: {} from {}", request.getFileName(), request.getFileLocation());

        // Heartbeat for long downloads
        Activity.getExecutionContext().heartbeat("Resolving file: " + request.getFileName());

        ResolvedFile resolved = fileResolver.resolve(request);

        log.info("File resolved to: {} (source: {}, temp: {})",
                resolved.getLocalPath(), resolved.getSourceType(), resolved.isTemporary());

        return resolved;
    }

    @Override
    public OcrResult performOcr(String localFilePath, OcrRequest request) {
        log.info("Performing OCR on: {} with language: {}", localFilePath, request.getLanguage());

        Activity.getExecutionContext().heartbeat("Starting OCR on: " + request.getFileName());

        OcrResult result = ocrService.performOcr(localFilePath, request);

        // Set the source file type from the request location
        result.setSourceFileType(FileResolver.detectSourceType(request.getFileLocation()));

        log.info("OCR completed: textFound={}, words={}, confidence={:.1f}%, time={}ms",
                result.isTextFound(), result.getWordCount(),
                result.getMeanConfidence(), result.getProcessingTimeMs());

        return result;
    }

    @Override
    public void cleanup(String localFilePath) {
        if (localFilePath == null || localFilePath.isBlank()) {
            return;
        }

        try {
            Path path = Paths.get(localFilePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("Cleaned up temporary file: {}", localFilePath);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up temporary file: {} - {}", localFilePath, e.getMessage());
        }
    }
}
