package com.fileprocessor.worker.activity;

import com.fileprocessor.activity.FileDetectionActivities;
import com.fileprocessor.model.DetectedFileType;
import com.fileprocessor.model.MimeDetectionResult;
import com.fileprocessor.worker.util.MimeTypeResolver;
import io.temporal.activity.Activity;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects MIME types using Apache Tika's content-based analysis
 * (magic bytes + filename heuristics).
 */
public class FileDetectionActivitiesImpl implements FileDetectionActivities {

    private static final Logger log = LoggerFactory.getLogger(FileDetectionActivitiesImpl.class);
    private final Tika tika = new Tika();

    @Override
    public MimeDetectionResult detectMimeType(String filePath) {
        log.info("Detecting MIME type for: {}", filePath);

        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw Activity.wrap(new IOException("File not found: " + filePath));
        }
        if (!Files.isReadable(path)) {
            throw Activity.wrap(new IOException("File not readable: " + filePath));
        }

        try {
            // Tika inspects magic bytes + extension
            String mimeType = tika.detect(path);
            DetectedFileType fileType = MimeTypeResolver.resolve(mimeType);

            log.info("Detected MIME={} → category={} for {}", mimeType, fileType, filePath);
            return new MimeDetectionResult(mimeType, fileType);

        } catch (IOException e) {
            log.error("MIME detection failed for {}", filePath, e);
            throw Activity.wrap(e);
        }
    }
}
