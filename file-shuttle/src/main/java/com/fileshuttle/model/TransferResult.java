package com.fileshuttle.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Result of a completed (or failed) transfer with performance metrics.
 */
public record TransferResult(
        TransferRequest request,
        boolean success,
        long bytesTransferred,
        Instant startTime,
        Instant endTime,
        long peakMemoryBytes,
        String errorMessage
) {
    public Duration duration() {
        return Duration.between(startTime, endTime);
    }

    public double throughputMBps() {
        long ms = duration().toMillis();
        if (ms == 0) return 0.0;
        return (bytesTransferred / (1024.0 * 1024.0)) / (ms / 1000.0);
    }

    public String peakMemoryFormatted() { return formatBytes(peakMemoryBytes); }
    public String bytesTransferredFormatted() { return formatBytes(bytesTransferred); }

    public String summary() {
        if (!success) {
            return """
                ✗ TRANSFER FAILED
                ─────────────────────────────
                  File:      %s
                  From:      %s → %s
                  To:        %s → %s
                  Error:     %s
                  Duration:  %s
                """.formatted(
                    request.fileName(),
                    request.inputType(), request.inputLocation(),
                    request.outputType(), request.outputLocation(),
                    errorMessage, formatDuration(duration()));
        }
        return """
            ✓ TRANSFER COMPLETE
            ─────────────────────────────
              File:        %s
              From:        %s (%s)
              To:          %s (%s)
              Size:        %s
              Duration:    %s
              Throughput:  %.2f MB/s
              Peak Memory: %s
            """.formatted(
                request.fileName(),
                request.inputType(), request.inputLocation(),
                request.outputType(), request.outputLocation(),
                bytesTransferredFormatted(),
                formatDuration(duration()),
                throughputMBps(),
                peakMemoryFormatted());
    }

    public static TransferResult success(TransferRequest req, long bytes,
                                         Instant start, Instant end, long peakMem) {
        return new TransferResult(req, true, bytes, start, end, peakMem, null);
    }

    public static TransferResult failure(TransferRequest req, String error,
                                         Instant start, long peakMem) {
        return new TransferResult(req, false, 0, start, Instant.now(), peakMem, error);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return "%.2f MB".formatted(bytes / (1024.0 * 1024));
        return "%.2f GB".formatted(bytes / (1024.0 * 1024 * 1024));
    }

    private static String formatDuration(Duration d) {
        long ms = d.toMillis();
        if (ms < 1000) return ms + " ms";
        if (ms < 60_000) return "%.2f s".formatted(ms / 1000.0);
        return "%d min %.1f s".formatted(d.toMinutesPart(),
                d.toSecondsPart() + d.toMillisPart() / 1000.0);
    }
}
