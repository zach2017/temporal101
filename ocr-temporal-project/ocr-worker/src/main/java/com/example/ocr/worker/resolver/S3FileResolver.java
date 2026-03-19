package com.example.ocr.worker.resolver;

import com.example.ocr.common.activity.OcrActivities.ResolvedFile;
import com.example.ocr.common.enums.FileSourceType;
import com.example.ocr.common.exception.OcrFileNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolves files from AWS S3 buckets.
 * Parses s3://bucket-name/path/to/key URIs and downloads to a temp directory.
 */
public class S3FileResolver {

    private static final Logger log = LoggerFactory.getLogger(S3FileResolver.class);

    private final S3Client s3Client;
    private final Path tempDir;

    public S3FileResolver(S3Client s3Client, Path tempDir) {
        this.s3Client = s3Client;
        this.tempDir = tempDir;
    }

    public ResolvedFile resolve(String s3Uri, String fileName) {
        String[] parsed = parseS3Uri(s3Uri);
        String bucket = parsed[0];
        String key = parsed[1];

        log.info("Downloading from S3: bucket={}, key={}", bucket, key);

        try {
            Path localPath = tempDir.resolve("s3_" + System.currentTimeMillis() + "_" + fileName);
            Files.createDirectories(localPath.getParent());

            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getReq)) {
                Files.copy(response, localPath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("S3 file downloaded to: {}", localPath);
            return new ResolvedFile(localPath.toString(), FileSourceType.S3, true);

        } catch (NoSuchKeyException e) {
            throw new OcrFileNotFoundException(s3Uri,
                    "S3 object not found: bucket=" + bucket + ", key=" + key, e);
        } catch (IOException e) {
            throw new OcrFileNotFoundException(s3Uri,
                    "Failed to download from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Parse an S3 URI into [bucket, key].
     * Input format: s3://bucket-name/path/to/object
     */
    static String[] parseS3Uri(String uri) {
        if (!uri.startsWith("s3://")) {
            throw new IllegalArgumentException("Invalid S3 URI: " + uri);
        }
        String path = uri.substring(5); // Remove "s3://"
        int slashIndex = path.indexOf('/');
        if (slashIndex <= 0) {
            throw new IllegalArgumentException("S3 URI must contain bucket and key: " + uri);
        }
        return new String[]{
                path.substring(0, slashIndex),
                path.substring(slashIndex + 1)
        };
    }
}
