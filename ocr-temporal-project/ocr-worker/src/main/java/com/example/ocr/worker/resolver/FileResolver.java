package com.example.ocr.worker.resolver;

import com.example.ocr.common.activity.OcrActivities.ResolvedFile;
import com.example.ocr.common.enums.FileSourceType;
import com.example.ocr.common.model.OcrRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a file location to a local file path.
 * Detects whether the source is S3, local filesystem, or HTTP URL,
 * and delegates to the appropriate resolver.
 */
public class FileResolver {

    private static final Logger log = LoggerFactory.getLogger(FileResolver.class);

    private final S3FileResolver s3Resolver;
    private final LocalFileResolver localResolver;
    private final UrlFileResolver urlResolver;

    public FileResolver(S3FileResolver s3Resolver,
                        LocalFileResolver localResolver,
                        UrlFileResolver urlResolver) {
        this.s3Resolver = s3Resolver;
        this.localResolver = localResolver;
        this.urlResolver = urlResolver;
    }

    /**
     * Detect the source type and resolve the file to a local path.
     */
    public ResolvedFile resolve(OcrRequest request) {
        String location = request.getFileLocation();
        FileSourceType sourceType = detectSourceType(location);

        log.info("Resolving file '{}' from {} source: {}",
                request.getFileName(), sourceType, location);

        return switch (sourceType) {
            case S3 -> s3Resolver.resolve(location, request.getFileName());
            case LOCAL -> localResolver.resolve(location, request.getFileName());
            case URL -> urlResolver.resolve(location, request.getFileName());
        };
    }

    /**
     * Detect the file source type based on the location string pattern.
     */
    public static FileSourceType detectSourceType(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("File location cannot be null or blank");
        }

        String trimmed = location.trim();

        if (trimmed.startsWith("s3://")) {
            return FileSourceType.S3;
        } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return FileSourceType.URL;
        } else {
            return FileSourceType.LOCAL;
        }
    }
}
