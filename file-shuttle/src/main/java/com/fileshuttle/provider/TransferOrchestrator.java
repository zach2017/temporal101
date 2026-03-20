package com.fileshuttle.provider;

import com.fileshuttle.metrics.MemoryTracker;
import com.fileshuttle.model.TransferRequest;
import com.fileshuttle.model.TransferResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;

/**
 * Core transfer engine: streams bytes from any input provider to any output
 * provider while collecting throughput and memory metrics.
 *
 * PIPELINE:
 *   ┌─────────────┐       ┌──────────────────┐       ┌──────────────┐
 *   │ Input       │       │  Orchestrator     │       │ Output       │
 *   │ Provider    │──────→│  (64KB buffer)    │──────→│ Provider     │
 *   │ .openInput()│ read  │  streamCopy loop  │ write │ .openOutput()│
 *   └─────────────┘       └──────────────────┘       └──────────────┘
 *
 * WHY NOT InputStream.transferTo?
 *   transferTo uses hardcoded 8192-byte buffer (too small for network I/O)
 *   and provides no progress callback. Our manual loop uses configurable
 *   buffer (64 KB default, optimal for network) and tracks bytes for metrics.
 *
 * MEMORY BUDGET (defaults):
 *   Read buffer:              64 KB
 *   Copy buffer (this class): 64 KB
 *   Write buffer (LocalFS):   64 KB
 *   Write buffer (S3):        8 MB (one multipart part)
 *   Write buffer (URL pipe):  64 KB
 *   ─────────────────────────────────
 *   WORST CASE Local→S3:    ~8.19 MB total
 */
public class TransferOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TransferOrchestrator.class);
    private final ProviderFactory providerFactory;

    public TransferOrchestrator(ProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    public TransferResult execute(TransferRequest request) {
        StorageProvider inProvider  = providerFactory.getProvider(request.inputType());
        StorageProvider outProvider = providerFactory.getProvider(request.outputType());

        log.info("Transfer: {} [{}] → {} [{}]",
                request.inputLocation(), inProvider.name(),
                request.outputLocation(), outProvider.name());

        MemoryTracker memTracker = new MemoryTracker();
        Instant start = Instant.now();
        memTracker.start();

        try (InputStream in  = inProvider.openInputStream(request);
             OutputStream out = outProvider.openOutputStream(request)) {

            long bytes = streamCopy(in, out, request.bufferSize());
            memTracker.stop();
            Instant end = Instant.now();

            TransferResult result = TransferResult.success(
                    request, bytes, start, end, memTracker.peakUsedBytes());
            log.info("Transfer complete: {}", result.summary());
            return result;

        } catch (Exception e) {
            memTracker.stop();
            log.error("Transfer failed: {}", e.getMessage(), e);
            return TransferResult.failure(request, e.getMessage(), start, memTracker.peakUsedBytes());
        }
    }

    /**
     * Manual stream copy with configurable buffer.
     * Returns total bytes copied.
     */
    private long streamCopy(InputStream in, OutputStream out, int bufferSize) throws Exception {
        byte[] buffer = new byte[bufferSize];
        long totalBytes = 0;
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
            if (totalBytes % (50 * 1024 * 1024) < bufferSize)
                log.debug("Progress: {} bytes transferred", totalBytes);
        }
        out.flush();
        return totalBytes;
    }
}
