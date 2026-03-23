package com.docprocessing.tika.activity;

import com.docprocessing.tika.model.TikaDetectionRequest;
import com.docprocessing.tika.model.TikaDetectionResult;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Temporal Activity that uses Apache Tika to detect a file's MIME type.
 *
 * <p>Registered on the {@code tika-detection-queue}.
 *
 * <p><strong>Fails immediately (non-retryable)</strong> if the file
 * does not exist, logging the exact path checked, the working
 * directory, and the contents of the parent directory to help
 * diagnose volume-mount issues.
 */
@ActivityInterface
public interface TikaDetectionActivity {

    @ActivityMethod(name = "detect_file_type_tika")
    TikaDetectionResult detectFileType(TikaDetectionRequest request);


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

            log.info("[detect_file_type_tika] Starting detection: file='{}' location='{}' hint='{}'",
                    fileName, fileLocation, existingHint);

            // ── Validate file exists ─────────────────────────
            Path path = Path.of(fileLocation);
            Path resolved = path.toAbsolutePath().normalize();
            Path cwd = Path.of("").toAbsolutePath();

            log.info("[detect_file_type_tika] Checking path: resolved='{}' cwd='{}'",
                    resolved, cwd);

            if (!Files.exists(path)) {
                String parentListing = listParentContents(resolved.getParent());

                String errorMsg = String.format(
                        "[detect_file_type_tika] FILE NOT FOUND: '%s' (resolved to '%s'). "
                                + "Working directory: '%s'. "
                                + "Parent directory '%s' contents: [%s]",
                        fileLocation, resolved, cwd,
                        resolved.getParent(), parentListing);

                log.error(errorMsg);

                throw ApplicationFailure.newNonRetryableFailure(
                        errorMsg, "FileNotFoundError");
            }

            if (!Files.isRegularFile(path)) {
                String errorMsg = String.format(
                        "[detect_file_type_tika] Path exists but is NOT a regular file: '%s' "
                                + "(resolved to '%s'). isDirectory=%s",
                        fileLocation, resolved, Files.isDirectory(path));

                log.error(errorMsg);

                throw ApplicationFailure.newNonRetryableFailure(
                        errorMsg, "FileNotFoundError");
            }

            if (!Files.isReadable(path)) {
                String errorMsg = String.format(
                        "[detect_file_type_tika] File exists but is NOT readable: '%s' "
                                + "(resolved to '%s'). Check file permissions.",
                        fileLocation, resolved);

                log.error(errorMsg);

                throw ApplicationFailure.newNonRetryableFailure(
                        errorMsg, "FileNotReadableError");
            }

            long fileSize;
            try {
                fileSize = Files.size(path);
            } catch (IOException e) {
                fileSize = -1;
            }

            log.info("[detect_file_type_tika] File validated OK: path='{}' size={} bytes",
                    resolved, fileSize);

            // ── If caller provided a MIME hint, trust it ─────
            if (existingHint != null && !existingHint.isBlank()
                    && !"application/octet-stream".equals(existingHint)) {
                log.info("[detect_file_type_tika] Using provided MIME hint: '{}'", existingHint);
                String category = TikaDetectionResult.categorise(existingHint);
                return new TikaDetectionResult(
                        fileName, fileLocation, existingHint, category, "");
            }

            // ── Content-based Tika detection ─────────────────
            String mimeType;
            String encoding = "";

            try {
                Metadata metadata = new Metadata();
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

                try (var stream = Files.newInputStream(path)) {
                    mimeType = TIKA.detect(stream, metadata);
                }

                String charset = metadata.get(Metadata.CONTENT_ENCODING);
                if (charset != null) {
                    encoding = charset;
                }

                log.info("[detect_file_type_tika] Tika content-detected: '{}' -> mime='{}' encoding='{}'",
                        fileName, mimeType, encoding);

            } catch (IOException e) {
                log.error("[detect_file_type_tika] Tika content detection FAILED for '{}': {}. "
                                + "Falling back to name-based detection.",
                        fileLocation, e.getMessage(), e);
                mimeType = TIKA.detect(fileName);
                log.info("[detect_file_type_tika] Name-based fallback: '{}' -> mime='{}'",
                        fileName, mimeType);
            }

            String category = TikaDetectionResult.categorise(mimeType);

            log.info("[detect_file_type_tika] Detection complete: file='{}' mime='{}' category='{}'",
                    fileName, mimeType, category);

            return new TikaDetectionResult(
                    fileName, fileLocation, mimeType, category, encoding);
        }

        /**
         * List up to 30 entries in the parent directory for diagnostic logging.
         */
        private static String listParentContents(Path parent) {
            if (parent == null || !Files.exists(parent)) {
                return "<parent does not exist>";
            }
            List<String> entries = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
                int count = 0;
                for (Path entry : stream) {
                    entries.add(entry.getFileName().toString());
                    if (++count >= 30) {
                        entries.add("... (truncated)");
                        break;
                    }
                }
            } catch (IOException e) {
                return "<error listing: " + e.getMessage() + ">";
            }
            return entries.isEmpty() ? "<empty>" : String.join(", ", entries);
        }
    }
}
