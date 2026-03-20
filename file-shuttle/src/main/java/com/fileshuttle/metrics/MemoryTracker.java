package com.fileshuttle.metrics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight JVM heap sampler — tracks peak memory during a transfer.
 * Runs on a Java 21 virtual thread, sampling every 50ms.
 * Overhead is negligible: Runtime.totalMemory/freeMemory are intrinsic calls.
 */
public class MemoryTracker {

    private static final long SAMPLE_INTERVAL_MS = 50;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong peak = new AtomicLong(0);
    private Thread samplerThread;

    public void start() {
        running.set(true);
        recordSample();
        samplerThread = Thread.ofVirtual().name("memory-sampler").start(() -> {
            while (running.get()) {
                recordSample();
                try { Thread.sleep(SAMPLE_INTERVAL_MS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        });
    }

    public void stop() {
        running.set(false);
        if (samplerThread != null) {
            samplerThread.interrupt();
            try { samplerThread.join(200); } catch (InterruptedException ignored) {}
        }
        recordSample();
    }

    public long peakUsedBytes() { return peak.get(); }

    private void recordSample() {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        peak.accumulateAndGet(used, Math::max);
    }
}
