package com.fileshuttle.provider;

import com.fileshuttle.model.TransferRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Provider for AWS S3 and S3-compatible stores (MinIO, LocalStack).
 *
 * INPUT (Download): S3Client.getObject returns a lazy HTTP chunked stream.
 *   Heap: only the read buffer (64 KB default).
 *
 * OUTPUT (Upload): S3MultipartOutputStream implements streaming multipart:
 *   1. createMultipartUpload → gets uploadId
 *   2. Bytes buffered into part buffer (8 MB default)
 *   3. When buffer fills → uploadPart sends to S3
 *   4. close() → uploads final part + completeMultipartUpload
 *   5. On exception → abortMultipartUpload
 *
 * MEMORY PROFILE:
 *   Download: 64 KB (one read buffer)
 *   Upload:   8 MB (one part buffer) — configurable via multipartPartSize
 *   Worst case full pipe: ~8.19 MB regardless of file size
 *
 * S3 URI FORMAT: s3://bucket-name/path/to/key
 */
public class S3Provider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(S3Provider.class);
    private final S3Client s3;

    public S3Provider() { this(buildDefaultClient()); }
    public S3Provider(S3Client s3) { this.s3 = s3; }

    /** Factory for MinIO / S3-compatible endpoints. */
    public static S3Provider forEndpoint(String endpoint, String accessKey,
                                         String secretKey, String region) {
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
        return new S3Provider(client);
    }

    @Override
    public InputStream openInputStream(TransferRequest request) throws IOException {
        S3Loc loc = S3Loc.parse(request.inputLocation());
        log.info("S3 GET s3://{}/{}", loc.bucket, loc.key);
        try {
            ResponseInputStream<GetObjectResponse> resp = s3.getObject(
                    GetObjectRequest.builder().bucket(loc.bucket).key(loc.key).build());
            return new BufferedInputStream(resp, request.bufferSize());
        } catch (S3Exception e) {
            throw new IOException("S3 download failed: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public OutputStream openOutputStream(TransferRequest request) throws IOException {
        S3Loc loc = S3Loc.parse(request.outputLocation());
        log.info("S3 multipart upload → s3://{}/{} (partSize={})",
                loc.bucket, loc.key, request.multipartPartSize());
        return new S3MultipartOutputStream(s3, loc.bucket, loc.key,
                (int) request.multipartPartSize());
    }

    @Override
    public long contentLength(TransferRequest request) throws IOException {
        S3Loc loc = S3Loc.parse(request.inputLocation());
        try {
            return s3.headObject(HeadObjectRequest.builder()
                    .bucket(loc.bucket).key(loc.key).build()).contentLength();
        } catch (S3Exception e) { return -1L; }
    }

    @Override
    public String name() { return "S3"; }

    // ── S3 URI parser ──────────────────────────────────────────────────
    private record S3Loc(String bucket, String key) {
        static S3Loc parse(String uri) {
            if (!uri.startsWith("s3://"))
                throw new IllegalArgumentException("S3 URI must start with s3:// — got: " + uri);
            String path = uri.substring(5);
            int slash = path.indexOf('/');
            if (slash < 0)
                throw new IllegalArgumentException("S3 URI missing key: " + uri);
            return new S3Loc(path.substring(0, slash), path.substring(slash + 1));
        }
    }

    // ── Streaming multipart upload OutputStream ────────────────────────
    /**
     * OutputStream that streams data to S3 via multipart upload.
     * Memory: holds exactly ONE part buffer at any time. Parts uploaded
     * as soon as the buffer fills, then the buffer is reused.
     */
    static class S3MultipartOutputStream extends OutputStream {
        private final S3Client s3;
        private final String bucket, key, uploadId;
        private final int partSize;
        private final byte[] buffer;
        private int pos = 0;
        private int partNumber = 1;
        private final List<CompletedPart> completedParts = new ArrayList<>();
        private boolean closed = false;

        S3MultipartOutputStream(S3Client s3, String bucket, String key, int partSize) {
            this.s3 = s3;
            this.bucket = bucket;
            this.key = key;
            this.partSize = partSize;
            this.buffer = new byte[partSize];
            this.uploadId = s3.createMultipartUpload(
                    CreateMultipartUploadRequest.builder()
                            .bucket(bucket).key(key).build()).uploadId();
        }

        @Override
        public void write(int b) throws IOException {
            buffer[pos++] = (byte) b;
            if (pos >= partSize) flushPart();
        }

        @Override
        public void write(byte[] data, int off, int len) throws IOException {
            int remaining = len, srcOff = off;
            while (remaining > 0) {
                int toCopy = Math.min(remaining, partSize - pos);
                System.arraycopy(data, srcOff, buffer, pos, toCopy);
                pos += toCopy;
                srcOff += toCopy;
                remaining -= toCopy;
                if (pos >= partSize) flushPart();
            }
        }

        private void flushPart() throws IOException {
            if (pos == 0) return;
            try {
                UploadPartResponse resp = s3.uploadPart(
                        UploadPartRequest.builder()
                                .bucket(bucket).key(key)
                                .uploadId(uploadId)
                                .partNumber(partNumber)
                                .contentLength((long) pos)
                                .build(),
                        software.amazon.awssdk.core.sync.RequestBody.fromBytes(
                                Arrays.copyOf(buffer, pos)));
                completedParts.add(CompletedPart.builder()
                        .partNumber(partNumber).eTag(resp.eTag()).build());
                partNumber++;
                pos = 0;
            } catch (S3Exception e) {
                abort();
                throw new IOException("S3 part upload failed: " + e.getMessage(), e);
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                flushPart();
                s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                        .bucket(bucket).key(key).uploadId(uploadId)
                        .multipartUpload(CompletedMultipartUpload.builder()
                                .parts(completedParts).build())
                        .build());
            } catch (Exception e) {
                abort();
                throw new IOException("S3 multipart complete failed", e);
            }
        }

        private void abort() {
            try {
                s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                        .bucket(bucket).key(key).uploadId(uploadId).build());
            } catch (Exception ignored) {}
        }
    }

    private static S3Client buildDefaultClient() {
        String endpoint = System.getenv("S3_ENDPOINT");
        var builder = S3Client.builder()
                .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        return builder.build();
    }
}
