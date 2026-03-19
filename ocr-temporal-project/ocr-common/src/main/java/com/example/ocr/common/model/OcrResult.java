package com.example.ocr.common.model;

import com.example.ocr.common.enums.FileSourceType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result DTO returned by the OCR workflow.
 * Contains extracted text, confidence metrics, and processing metadata.
 */
public class OcrResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Extracted plain text. Empty string (never null) if no text found. */
    private String text = "";

    /** HOCR XML output. Null if not requested. */
    private String hocrOutput;

    /** TSV output with word-level bounding boxes. Null if not requested. */
    private String tsvOutput;

    /** Average word confidence (0-100). Below 50 suggests poor quality. */
    private float meanConfidence;

    /** Number of words detected. */
    private int wordCount;

    /** False if no meaningful text was extracted. */
    private boolean textFound;

    /** Non-fatal warnings (low confidence, low DPI, etc.). */
    private List<String> warnings = new ArrayList<>();

    /** Language(s) used for recognition. */
    private String language;

    /** Time spent in OCR processing (milliseconds). */
    private long processingTimeMs;

    /** How the source file was resolved. */
    private FileSourceType sourceFileType;

    /** Original file name from the request. */
    private String sourceFileName;

    public OcrResult() {
    }

    // ---- Static builder ----

    public static OcrResultBuilder builder() {
        return new OcrResultBuilder();
    }

    // ---- Getters ----

    public String getText() { return text; }
    public String getHocrOutput() { return hocrOutput; }
    public String getTsvOutput() { return tsvOutput; }
    public float getMeanConfidence() { return meanConfidence; }
    public int getWordCount() { return wordCount; }
    public boolean isTextFound() { return textFound; }
    public List<String> getWarnings() { return warnings; }
    public String getLanguage() { return language; }
    public long getProcessingTimeMs() { return processingTimeMs; }
    public FileSourceType getSourceFileType() { return sourceFileType; }
    public String getSourceFileName() { return sourceFileName; }

    // ---- Setters for serialization ----

    public void setText(String text) { this.text = text; }
    public void setHocrOutput(String hocrOutput) { this.hocrOutput = hocrOutput; }
    public void setTsvOutput(String tsvOutput) { this.tsvOutput = tsvOutput; }
    public void setMeanConfidence(float meanConfidence) { this.meanConfidence = meanConfidence; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }
    public void setTextFound(boolean textFound) { this.textFound = textFound; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public void setLanguage(String language) { this.language = language; }
    public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    public void setSourceFileType(FileSourceType sourceFileType) { this.sourceFileType = sourceFileType; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }

    @Override
    public String toString() {
        return "OcrResult{" +
                "textFound=" + textFound +
                ", wordCount=" + wordCount +
                ", confidence=" + meanConfidence +
                ", language='" + language + '\'' +
                ", processingTimeMs=" + processingTimeMs +
                ", warnings=" + warnings.size() +
                '}';
    }

    // ---- Builder ----

    public static class OcrResultBuilder {
        private final OcrResult result = new OcrResult();

        public OcrResultBuilder text(String val) { result.text = val; return this; }
        public OcrResultBuilder hocrOutput(String val) { result.hocrOutput = val; return this; }
        public OcrResultBuilder tsvOutput(String val) { result.tsvOutput = val; return this; }
        public OcrResultBuilder meanConfidence(float val) { result.meanConfidence = val; return this; }
        public OcrResultBuilder wordCount(int val) { result.wordCount = val; return this; }
        public OcrResultBuilder textFound(boolean val) { result.textFound = val; return this; }
        public OcrResultBuilder warnings(List<String> val) { result.warnings = val; return this; }
        public OcrResultBuilder language(String val) { result.language = val; return this; }
        public OcrResultBuilder processingTimeMs(long val) { result.processingTimeMs = val; return this; }
        public OcrResultBuilder sourceFileType(FileSourceType val) { result.sourceFileType = val; return this; }
        public OcrResultBuilder sourceFileName(String val) { result.sourceFileName = val; return this; }
        public OcrResultBuilder addWarning(String warning) { result.warnings.add(warning); return this; }

        public OcrResult build() {
            return result;
        }
    }
}
