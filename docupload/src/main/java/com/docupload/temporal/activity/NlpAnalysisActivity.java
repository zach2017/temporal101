package com.docupload.temporal.activity;

import com.docupload.temporal.workflow.DocumentWorkflowRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;
import java.util.Map;

/**
 * Temporal Activity interface — Stage 3: NLP Analysis &amp; Indexing.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Run entity recognition (people, organisations, dates, amounts)</li>
 *   <li>Generate a structured summary (title, sections, key points)</li>
 *   <li>Classify document type (contract, report, invoice, etc.)</li>
 *   <li>Emit the enriched payload for downstream indexing</li>
 * </ul>
 *
 * Implementation class: {@code NlpAnalysisActivityImpl}
 * (to be created when wiring real workers)
 */
@ActivityInterface
public interface NlpAnalysisActivity {

    /**
     * Enrich and index the extracted text.
     *
     * @param request          original workflow input (jobId, fileName, etc.)
     * @param extractionResult raw text + statistics from the extraction stage
     * @return                 structured analysis result
     */
    @ActivityMethod
    AnalysisResult analyze(DocumentWorkflowRequest request,
                           TextExtractionActivity.ExtractionResult extractionResult);

    // ── Nested result type ────────────────────────────────────────────────────

    /**
     * Result produced by {@link #analyze}.
     */
    class AnalysisResult {
        private String              documentType;      // e.g. "REPORT", "CONTRACT", "INVOICE"
        private String              summary;
        private List<String>        keyTopics;
        private Map<String, String> namedEntities;     // entity text → entity type
        private String              language;
        private double              confidenceScore;   // 0.0–1.0
        private long                durationMs;

        public AnalysisResult() {}

        public String              getDocumentType()              { return documentType; }
        public void                setDocumentType(String v)      { this.documentType = v; }

        public String              getSummary()                   { return summary; }
        public void                setSummary(String v)           { this.summary = v; }

        public List<String>        getKeyTopics()                 { return keyTopics; }
        public void                setKeyTopics(List<String> v)   { this.keyTopics = v; }

        public Map<String, String> getNamedEntities()             { return namedEntities; }
        public void                setNamedEntities(Map<String,String> v) { this.namedEntities = v; }

        public String              getLanguage()                  { return language; }
        public void                setLanguage(String v)          { this.language = v; }

        public double              getConfidenceScore()           { return confidenceScore; }
        public void                setConfidenceScore(double v)   { this.confidenceScore = v; }

        public long                getDurationMs()                { return durationMs; }
        public void                setDurationMs(long v)          { this.durationMs = v; }
    }
}
