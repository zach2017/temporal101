package com.fileshuttle.provider;

import com.fileshuttle.model.TransferRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Core abstraction for reading/writing bytes against a storage backend.
 *
 * STREAMING CONTRACT:
 * - openInputStream returns a lazy stream — data pulled on read. Never buffered fully.
 * - openOutputStream accepts incremental writes. May internally buffer one multipart
 *   part (S3) but NEVER the whole file.
 * - Callers must close both streams (try-with-resources).
 *
 * THREAD SAFETY:
 * Implementations must be safe for concurrent use across different TransferRequests.
 */
public interface StorageProvider {

    /** Opens a streaming read handle. Caller must close. */
    InputStream openInputStream(TransferRequest request) throws IOException;

    /** Opens a streaming write handle. close() triggers final flush / multipart complete. */
    OutputStream openOutputStream(TransferRequest request) throws IOException;

    /** Content length of source, or -1 if unknown. For progress reporting. */
    default long contentLength(TransferRequest request) throws IOException {
        return -1L;
    }

    /** Human-readable name for logging. */
    String name();
}
