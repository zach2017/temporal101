package com.docprocessing.tika.activity;

import com.docprocessing.tika.model.TikaDetectionRequest;
import com.docprocessing.tika.model.TikaDetectionResult;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Temporal Activity that uses Apache Tika to detect a file's MIME type.
 *
 * <p>Registered on the {@code tika-detection-queue}.  The Python
 * {@code DocumentIntakeWorkflow} calls this activity to get an
 * authoritative MIME type before routing to the correct extraction
 * pipeline.
 *
 * <p>Tika performs <strong>content-based</strong> detection (magic bytes)
 * when the file is locally accessible, falling back to filename-based
 * detection otherwise.
 */
@ActivityInterface
public interface TikaDetectionActivity {

    @ActivityMethod(name = "detect_file_type_tika")
    TikaDetectionResult detectFileType(TikaDetectionRequest request);


    /**
     * Default implementation backed by Apache Tika.
     */
    class Impl implements TikaDetectionActivity {

        private static final Logger log = LoggerFactory.getLogger(Impl.class);
        private static final Tika TIKA = new Tika();

        @Override
        public TikaDetectionResult detectFileType(TikaDetectionRequest request) {
            String fileName = request.fileName();
            String fileLocation = request.fileLocation();
            String existingHint = request.fileType();

            Activity.getExecutionContext().heartbeat(
                    "Detecting MIME type for " + fileName);

            log.info("Tika detection starting: file={} location={} hint={}",
                    fileName, fileLocation, existingHint);

            // If caller already provided a confident MIME type, trust it
            if (existingHint != null && !existingHint.isBlank()
                    && !"application/octet-stream".equals(existingHint)) {
                log.info("Using provided MIME hint: {}", existingHint);
                String category = TikaDetectionResult.categorise(existingHint);
                return new TikaDetectionResult(
                        fileName, fileLocation, existingHint, category, "");
            }

            String mimeType;
            String encoding = "";

            // Content-based detection (reads magic bytes)
            Path path = Path.of(fileLocation);
            if (Files.isReadable(path)) {
                try {
                    // Full detection with metadata
                    Metadata metadata = new Metadata();
                    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

                    try (var stream = Files.newInputStream(path)) {
                        mimeType = TIKA.detect(stream, metadata);
                    }

                    // Extract encoding if available
                    String charset = metadata.get(Metadata.CONTENT_ENCODING);
                    if (charset != null) {
                        encoding = charset;
                    }

                    log.info("Tika content-detected: {} → {} (encoding={})",
                            fileName, mimeType, encoding);

                } catch (IOException e) {
                    log.warn("Tika content detection failed, falling back to name: {}",
                            e.getMessage());
                    mimeType = TIKA.detect(fileName);
                }
            } else {
                // File not locally accessible — name-based only
                log.info("File not locally readable, using name-based detection");
                mimeType = TIKA.detect(fileName);
            }

            String category = TikaDetectionResult.categorise(mimeType);

            log.info("Tika detection complete: file={} mime={} category={}",
                    fileName, mimeType, category);

            return new TikaDetectionResult(
                    fileName, fileLocation, mimeType, category, encoding);
        }
    }
}
