package com.docupload.temporal.activity.impl;

import com.docupload.temporal.activity.NlpAnalysisActivity;
import com.docupload.temporal.activity.TextExtractionActivity;
import com.docupload.temporal.workflow.DocumentWorkflowRequest;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.spring.boot.ActivityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ════════════════════════════════════════════════════════════════
 *  STAGE 3 — NLP Analysis & Indexing  (simulated)
 * ════════════════════════════════════════════════════════════════
 *
 * HOW TO WIRE TO TEMPORAL
 * ───────────────────────
 * Same pattern as the other activity impls.  The @ActivityImpl annotation
 * + Spring component scan are the only wiring needed.
 *
 * In production this class would call:
 *   • An NLP service (spaCy, Stanford NLP, AWS Comprehend) for NER
 *   • An LLM API (OpenAI, Anthropic) for abstractive summarisation
 *   • A search index (Elasticsearch, OpenSearch) to persist the result
 *
 * SIMULATION
 * ──────────
 * Sleeps 1 500 ms to simulate NLP inference latency, then returns
 * a static set of entities, topics, and a document classification.
 */
@Component
@ActivityImpl(taskQueues = "DOCUMENT_PROCESSING_TASK_QUEUE")
public class NlpAnalysisActivityImpl implements NlpAnalysisActivity {

    private static final Logger log = LoggerFactory.getLogger(NlpAnalysisActivityImpl.class);

    // ── Static NLP result ─────────────────────────────────────────────────────

    private static final List<String> STATIC_KEY_TOPICS = List.of(
            "Digital Transformation",
            "Cloud Architecture",
            "Microservices",
            "Risk Management",
            "Project Governance",
            "Kubernetes / EKS",
            "Apache Kafka",
            "CI/CD Pipeline"
    );

    private static final Map<String, String> STATIC_NAMED_ENTITIES = new LinkedHashMap<>() {{
        put("Jane R. Holloway",   "PERSON — Principal Solutions Architect");
        put("Marcus T. Webb",     "PERSON — VP Engineering");
        put("Sandra L. Chen",     "Chief Technology Officer");
        put("Deloitte Digital",   "ORGANISATION — Consulting Partner");
        put("AWS EKS",            "TECHNOLOGY — Container Orchestration");
        put("Apache Kafka",       "TECHNOLOGY — Event Streaming");
        put("PostgreSQL 16",      "TECHNOLOGY — Relational Database");
        put("Redis 7.2",          "TECHNOLOGY — In-Memory Cache");
        put("Keycloak 24.x",      "TECHNOLOGY — Identity Provider");
        put("Grafana Cloud",      "TECHNOLOGY — Observability Platform");
        put("$4.2M",              "MONEY — Total Investment");
        put("340%",               "PERCENT — 36-month projected ROI");
        put("Q2 2025",            "DATE — Infrastructure modernisation deadline");
        put("March 2025",         "DATE — Document sign-off date");
        put("Thursday 09:00 EST", "TIME — Weekly steering committee");
    }};

    @Override
    public AnalysisResult analyze(DocumentWorkflowRequest request,
                                  TextExtractionActivity.ExtractionResult extractionResult) {
        long start = System.currentTimeMillis();
        ActivityExecutionContext ctx = Activity.getExecutionContext();

        log.info("[NLP] Starting analysis — jobId={} words={}",
                request.getJobId(), extractionResult.getWordCount());

        // ── Step 1: Tokenisation & sentence splitting (300 ms) ───────────────
        simulateWork(ctx, 300, "Tokenising document…");

        // ── Step 2: Named-entity recognition (700 ms) ────────────────────────
        simulateWork(ctx, 700, "Running named-entity recognition…");

        // ── Step 3: Document classification + summarisation (500 ms) ─────────
        simulateWork(ctx, 500, "Classifying document type…");

        long durationMs = System.currentTimeMillis() - start;

        AnalysisResult result = new AnalysisResult();
        result.setDocumentType("STRATEGIC_REPORT");
        result.setSummary(buildSummary(request, extractionResult));
        result.setKeyTopics(STATIC_KEY_TOPICS);
        result.setNamedEntities(STATIC_NAMED_ENTITIES);
        result.setLanguage("en-US");
        result.setConfidenceScore(0.94);
        result.setDurationMs(durationMs);

        log.info("[NLP] Complete — jobId={} type={} entities={} durationMs={}",
                request.getJobId(), result.getDocumentType(),
                result.getNamedEntities().size(), durationMs);
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

    private static String buildSummary(DocumentWorkflowRequest req,
                                        TextExtractionActivity.ExtractionResult ext) {
        return String.format(
                "Strategic report '%s' (%d words, %d pages) describing the Digital " +
                "Transformation Program with $4.2M investment and 340%% ROI projection. " +
                "Identifies 5 key risks and 4 delivery phases through Q4 2025. " +
                "Processed via Temporal pipeline — jobId=%s.",
                req.getOriginalFileName(), ext.getWordCount(), ext.getPageCount(), req.getJobId()
        );
    }
}
