package com.docupload.temporal.activity;

import com.docupload.temporal.workflow.DocumentWorkflowRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Temporal Activity interface — Stage 2: OCR &amp; Text Extraction.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Retrieve the raw file from object storage using {@code storageKey}</li>
 *   <li>Route to the appropriate parser based on MIME type:
 *       <ul>
 *         <li>PDF  → Apache PDFBox / Tika</li>
 *         <li>DOCX → Apache POI</li>
 *         <li>PNG/JPG → Tesseract OCR</li>
 *         <li>TXT  → Direct read</li>
 *       </ul>
 *   </li>
 *   <li>Return the raw extracted text and page/word counts</li>
 * </ul>
 *
 * Implementation class: {@code TextExtractionActivityImpl}
 * (to be created when wiring real workers)
 */
@ActivityInterface
public interface TextExtractionActivity {

    /**
     * Extract raw text from the document.
     *
     * @param request     the workflow input with storage reference
     * @param scanResult  the verified MIME type from the preceding scan stage
     * @return            extracted text and document statistics
     */
    @ActivityMethod
    ExtractionResult extract(DocumentWorkflowRequest request,
                              SecurityScanActivity.ScanResult scanResult);

    // ── Nested result type ────────────────────────────────────────────────────

    /**
     * Result produced by {@link #extract}.
     */
    class ExtractionResult {
        private String rawText;
        private int    pageCount;
        private int    wordCount;
        private int    characterCount;
        private String parserUsed;      // e.g. "PDFBox", "Tesseract", "ApachePOI"
        private long   durationMs;

        public ExtractionResult() {}

        public String getRawText()             { return rawText; }
        public void   setRawText(String v)     { this.rawText = v; }

        public int    getPageCount()           { return pageCount; }
        public void   setPageCount(int v)      { this.pageCount = v; }

        public int    getWordCount()           { return wordCount; }
        public void   setWordCount(int v)      { this.wordCount = v; }

        public int    getCharacterCount()      { return characterCount; }
        public void   setCharacterCount(int v) { this.characterCount = v; }

        public String getParserUsed()          { return parserUsed; }
        public void   setParserUsed(String v)  { this.parserUsed = v; }

        public long   getDurationMs()          { return durationMs; }
        public void   setDurationMs(long v)    { this.durationMs = v; }
    }
}
