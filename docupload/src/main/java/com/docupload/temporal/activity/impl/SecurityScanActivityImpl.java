package com.docupload.temporal.activity.impl;

import com.docupload.temporal.activity.SecurityScanActivity;
import com.docupload.temporal.workflow.DocumentWorkflowRequest;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.spring.boot.ActivityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

/**
 * ════════════════════════════════════════════════════════════════
 *  STAGE 1 — Security Scan & Validation  (simulated)
 * ════════════════════════════════════════════════════════════════
 *
 * HOW TO WIRE TO TEMPORAL
 * ───────────────────────
 * 1. This class is annotated with @ActivityImpl(taskQueues = TASK_QUEUE).
 *    The temporal-spring-boot-autoconfigure starter scans for this
 *    annotation and auto-registers the bean on the named task queue.
 *
 * 2. Make sure application.properties contains:
 *      spring.temporal.workers-auto-discovery.packages=com.docupload.temporal
 *    so the autoconfigure starter finds this class.
 *
 * 3. Ensure the Temporal dev server is running:
 *      temporal server start-dev
 *    (installs via: brew install temporal  OR  go install go.temporal.io/server/cmd/temporal@latest)
 *
 * 4. The worker connects to 127.0.0.1:7233 (configured in application.properties).
 *
 * SIMULATION
 * ──────────
 * Simulates a 2-second virus/MIME scan with heartbeats so Temporal
 * knows the activity is alive.  Always returns CLEAN for demo purposes.
 */
@Component
@ActivityImpl(taskQueues = "DOCUMENT_PROCESSING_TASK_QUEUE")
public class SecurityScanActivityImpl implements SecurityScanActivity {

    private static final Logger log = LoggerFactory.getLogger(SecurityScanActivityImpl.class);

    /** Allowed MIME types for this demo pipeline. */
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "image/png",
            "image/jpeg"
    );

    @Override
    public ScanResult scan(DocumentWorkflowRequest request) {
        long start = System.currentTimeMillis();
        ActivityExecutionContext ctx = Activity.getExecutionContext();

        log.info("[SCAN] Starting security scan — jobId={} file={} size={}B",
                request.getJobId(), request.getOriginalFileName(), request.getFileSizeBytes());

        // ── Step 1: MIME-type validation (simulated, 500 ms) ─────────────────
        simulateWork(ctx, 500, "Validating MIME type…");
        String detectedMime = resolveMime(request.getContentType(), request.getOriginalFileName());
        boolean mimeAllowed = ALLOWED_MIME_TYPES.contains(detectedMime);

        if (!mimeAllowed) {
            log.warn("[SCAN] Rejected — unsupported MIME type: {}", detectedMime);
            return new ScanResult(false, "UNSUPPORTED_TYPE", detectedMime,
                    System.currentTimeMillis() - start);
        }

        // ── Step 2: File-size guard (instant) ────────────────────────────────
        if (request.getFileSizeBytes() > 50 * 1024 * 1024L) {
            log.warn("[SCAN] Rejected — file too large: {}B", request.getFileSizeBytes());
            return new ScanResult(false, "FILE_TOO_LARGE", detectedMime,
                    System.currentTimeMillis() - start);
        }

        // ── Step 3: Simulated virus scan (1 500 ms with heartbeats) ─────────
        simulateWork(ctx, 1_500, "Running antivirus engine…");

        // 5% random threat detection rate for realism in demos
        boolean threatDetected = new Random().nextInt(100) < 5;
        if (threatDetected) {
            log.warn("[SCAN] Threat detected in file: {}", request.getOriginalFileName());
            return new ScanResult(false, "THREAT_DETECTED", detectedMime,
                    System.currentTimeMillis() - start);
        }

        long durationMs = System.currentTimeMillis() - start;
        log.info("[SCAN] CLEAN — jobId={} mime={} durationMs={}", request.getJobId(), detectedMime, durationMs);

        return new ScanResult(true, "CLEAN", detectedMime, durationMs);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Sleep for {@code millis} ms while sending Temporal heartbeats every 200 ms
     * so the server knows this activity is still alive.
     * Without heartbeats a long-running activity would be treated as timed-out.
     */
    private static void simulateWork(ActivityExecutionContext ctx, long millis, String detail) {
        long remaining = millis;
        while (remaining > 0) {
            long chunk = Math.min(200, remaining);
            try { Thread.sleep(chunk); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            ctx.heartbeat(detail);   // tell Temporal we're alive
            remaining -= chunk;
        }
    }

    /**
     * Resolve a canonical MIME type from the declared content-type or file extension.
     * Real implementations would use Apache Tika for magic-byte detection.
     */
    private static String resolveMime(String declared, String fileName) {
        if (declared != null && !declared.isBlank() && !declared.equals("application/octet-stream"))
            return declared.split(";")[0].trim().toLowerCase();
        if (fileName == null) return "application/octet-stream";
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";
        return switch (ext) {
            case "pdf"  -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt"  -> "text/plain";
            case "png"  -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default     -> "application/octet-stream";
        };
    }
}
