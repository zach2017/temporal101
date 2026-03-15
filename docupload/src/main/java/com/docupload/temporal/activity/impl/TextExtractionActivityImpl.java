package com.docupload.temporal.activity.impl;

import com.docupload.temporal.activity.SecurityScanActivity;
import com.docupload.temporal.activity.TextExtractionActivity;
import com.docupload.temporal.workflow.DocumentWorkflowRequest;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.spring.boot.ActivityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ════════════════════════════════════════════════════════════════
 *  STAGE 2 — OCR & Text Extraction  (simulated)
 * ════════════════════════════════════════════════════════════════
 *
 * HOW TO WIRE TO TEMPORAL
 * ───────────────────────
 * Registered automatically via @ActivityImpl + Spring component scan.
 * No extra wiring required beyond what SecurityScanActivityImpl describes.
 *
 * In a real implementation this class would:
 *   • Download the file from object storage using request.getStorageKey()
 *   • Route to the correct parser based on scanResult.getMimeType():
 *       PDF  → Apache PDFBox  (pdfbox-app:3.x)
 *       DOCX → Apache POI     (poi-ooxml:5.x)
 *       IMG  → Tesseract OCR  (tesseract4j:5.x)
 *       TXT  → Files.readString()
 *
 * SIMULATION
 * ──────────
 * Sleeps 3 seconds (with heartbeats) to simulate heavy OCR work,
 * then returns a rich static document as the "extracted" text.
 */
@Component
@ActivityImpl(taskQueues = "DOCUMENT_PROCESSING_TASK_QUEUE")
public class TextExtractionActivityImpl implements TextExtractionActivity {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionActivityImpl.class);

    // ── Static extraction result — returned for every file in the demo ────────
    private static final String STATIC_EXTRACTED_TEXT = """
            ══════════════════════════════════════════════════════════════════
            DOCUMENT EXTRACTION REPORT  [Temporal Worker Pipeline]
            ══════════════════════════════════════════════════════════════════

            EXECUTIVE SUMMARY
            ──────────────────
            This document contains Q4 2024 Strategic Initiative data for the
            Digital Transformation Program (DTP) across all business units.
            Processed by Temporal worker on task queue: DOCUMENT_PROCESSING_TASK_QUEUE

            SECTION 1 — PROJECT OVERVIEW
            ─────────────────────────────
            The DTP was initiated in response to board-level directives to modernise
            core infrastructure by Q2 2025.  Estimated total investment: $4.2M with
            projected ROI of 340%% over a 36-month horizon.

            Key stakeholders:
              • CTO Office
              • VP of Engineering
              • Director of Product
              • External partner: Deloitte Digital

            Weekly steering reviews: every Thursday 09:00 EST.

            SECTION 2 — TECHNICAL SPECIFICATIONS
            ──────────────────────────────────────
            Cloud-native microservices on AWS EKS (Kubernetes).
            Services communicate via Apache Kafka (50 000 msg/s target).
            Persistence: PostgreSQL 16 with read replicas + Redis 7.2 L2 cache.

              API Gateway     : AWS API Gateway v2 (HTTP API)
              Authentication  : OAuth 2.0 / OIDC via Keycloak 24.x
              Service Mesh    : Istio 1.21 — mTLS enforced cluster-wide
              Observability   : OpenTelemetry → Grafana Cloud
              CI/CD           : GitHub Actions → ArgoCD → EKS

            SECTION 3 — RISK REGISTER
            ──────────────────────────
            ID    Description                          Likelihood  Impact    Owner
            R-01  Key personnel departure              Medium      Critical  HR/CTO
            R-02  Third-party API deprecation          High        High      Platform
            R-03  SOC 2 Type II compliance gap         Low         Critical  Security
            R-04  Budget overrun / scope creep         Medium      High      PMO
            R-05  Legacy ERP integration delay         High        Medium    IT Ops

            SECTION 4 — MILESTONES
            ────────────────────────
            Phase 1 (Jan–Mar 2025): Discovery & Architecture        ✓ COMPLETE
            Phase 2 (Apr–Jun 2025): Core Platform Build & Unit Test ⚙ IN PROGRESS
            Phase 3 (Jul–Sep 2025): Integration Testing & UAT       ○ PENDING
            Phase 4 (Oct–Dec 2025): Production Rollout & Hypercare  ○ PENDING

            SECTION 5 — SIGN-OFF
            ──────────────────────
            Prepared : Jane R. Holloway  — Principal Solutions Architect
            Reviewed : Marcus T. Webb    — VP Engineering
            Approved : Sandra L. Chen    — Chief Technology Officer
            Date     : March 2025
            Class    : INTERNAL — CONFIDENTIAL
            """;

    @Override
    public ExtractionResult extract(DocumentWorkflowRequest request,
                                    SecurityScanActivity.ScanResult scanResult) {
        long start = System.currentTimeMillis();
        ActivityExecutionContext ctx = Activity.getExecutionContext();

        log.info("[EXTRACT] Starting extraction — jobId={} file={} mime={}",
                request.getJobId(), request.getOriginalFileName(), scanResult.getMimeType());

        // ── Step 1: Simulate file retrieval from object storage (500 ms) ─────
        simulateWork(ctx, 500, "Retrieving file from storage…");

        // ── Step 2: Simulate parser init + OCR processing (2 000 ms) ─────────
        String parser = resolveParser(scanResult.getMimeType());
        log.info("[EXTRACT] Using parser: {}", parser);
        simulateWork(ctx, 2_000, "Running " + parser + "…");

        // ── Step 3: Simulate post-processing / layout analysis (500 ms) ──────
        simulateWork(ctx, 500, "Post-processing layout…");

        long durationMs = System.currentTimeMillis() - start;

        ExtractionResult result = new ExtractionResult();
        result.setRawText(STATIC_EXTRACTED_TEXT);
        result.setPageCount(12);
        result.setWordCount(countWords(STATIC_EXTRACTED_TEXT));
        result.setCharacterCount(STATIC_EXTRACTED_TEXT.length());
        result.setParserUsed(parser);
        result.setDurationMs(durationMs);

        log.info("[EXTRACT] Complete — jobId={} words={} durationMs={}",
                request.getJobId(), result.getWordCount(), durationMs);
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void simulateWork(ActivityExecutionContext ctx, long millis, String detail) {
        long remaining = millis;
        while (remaining > 0) {
            long chunk = Math.min(200, remaining);
            try { Thread.sleep(chunk); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            ctx.heartbeat(detail);
            remaining -= chunk;
        }
    }

    private static String resolveParser(String mime) {
        if (mime == null) return "GenericParser";
        return switch (mime) {
            case "application/pdf"  -> "PDFBox-3.x";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "ApachePOI-5.x";
            case "image/png", "image/jpeg" -> "Tesseract-OCR-5.x";
            case "text/plain" -> "DirectReader";
            default -> "ApacheTika-2.x";
        };
    }

    private static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }
}
