package com.example.ocr.worker.resolver;

import com.example.ocr.common.activity.OcrActivities.ResolvedFile;
import com.example.ocr.common.constants.OcrConstants;
import com.example.ocr.common.enums.FileSourceType;
import com.example.ocr.common.exception.OcrFileNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Resolves files from HTTP/HTTPS URLs.
 * Downloads the file to a local temp directory with configurable timeouts.
 */
public class UrlFileResolver {

    private static final Logger log = LoggerFactory.getLogger(UrlFileResolver.class);

    private final HttpClient httpClient;
    private final Path tempDir;
    private final long maxFileSize;

    public UrlFileResolver(Path tempDir) {
        this(tempDir, OcrConstants.MAX_FILE_SIZE_BYTES);
    }

    public UrlFileResolver(Path tempDir, long maxFileSize) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.tempDir = tempDir;
        this.maxFileSize = maxFileSize;
    }

    public ResolvedFile resolve(String url, String fileName) {
        log.info("Downloading from URL: {}", url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new OcrFileNotFoundException(url,
                        "HTTP download failed with status " + response.statusCode());
            }

            // Check content length if available
            response.headers().firstValueAsLong("content-length").ifPresent(length -> {
                if (length > maxFileSize) {
                    throw new OcrFileNotFoundException(url,
                            "File exceeds maximum size: " + length + " bytes (max: " + maxFileSize + ")");
                }
            });

            Path localPath = tempDir.resolve("url_" + System.currentTimeMillis() + "_" + fileName);
            Files.createDirectories(localPath.getParent());

            try (InputStream body = response.body()) {
                Files.copy(body, localPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Verify downloaded size
            long downloadedSize = Files.size(localPath);
            if (downloadedSize > maxFileSize) {
                Files.deleteIfExists(localPath);
                throw new OcrFileNotFoundException(url,
                        "Downloaded file exceeds maximum size: " + downloadedSize + " bytes");
            }

            log.info("URL file downloaded to: {} ({} bytes)", localPath, downloadedSize);
            return new ResolvedFile(localPath.toString(), FileSourceType.URL, true);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrFileNotFoundException(url,
                    "Failed to download from URL: " + e.getMessage(), e);
        }
    }
}
