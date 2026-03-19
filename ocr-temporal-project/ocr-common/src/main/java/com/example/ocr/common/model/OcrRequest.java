package com.example.ocr.common.model;

import com.example.ocr.common.enums.OcrEngineMode;
import com.example.ocr.common.enums.OutputFormat;
import com.example.ocr.common.enums.PageSegmentationMode;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Set;

/**
 * Request DTO for the OCR workflow.
 * Contains all information needed to locate a file and perform OCR.
 *
 * <p>Clients construct this object and pass it to {@code OcrWorkflow.processImage()}.
 */
public class OcrRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Name of the file to process. */
    private String fileName;

    /**
     * Location of the file. Supports three formats:
     * <ul>
     *   <li>S3 URI: {@code s3://bucket-name/path/to/file.png}</li>
     *   <li>Local path: {@code /mnt/shared/files/document.tiff}</li>
     *   <li>HTTP URL: {@code https://example.com/images/scan.jpg}</li>
     * </ul>
     */
    private String fileLocation;

    /** Tesseract language code(s). Use '+' for multiple: "eng+deu+fra". Default: "eng". */
    private String language = "eng";

    /** Tesseract OCR engine mode. Default: LSTM_ONLY. */
    private OcrEngineMode ocrEngineMode = OcrEngineMode.LSTM_ONLY;

    /** Tesseract page segmentation mode. Default: FULLY_AUTOMATIC. */
    private PageSegmentationMode pageSegMode = PageSegmentationMode.FULLY_AUTOMATIC;

    /** Target DPI for preprocessing. Images below this are upscaled. Default: 300. */
    private int dpi = 300;

    /** If set, restricts OCR to only these characters (e.g., "0123456789" for digits). */
    private String charWhitelist;

    /** Preserve spacing between words. Useful for tables and TOC layouts. Default: false. */
    private boolean preserveInterwordSpaces = false;

    /** Desired output formats. Default: {PLAIN_TEXT}. */
    private Set<OutputFormat> outputFormats = EnumSet.of(OutputFormat.PLAIN_TEXT);

    /** Image preprocessing configuration. */
    private PreprocessingOptions preprocessingOptions = PreprocessingOptions.defaults();

    public OcrRequest() {
    }

    // ---- Static builder ----

    public static OcrRequestBuilder builder() {
        return new OcrRequestBuilder();
    }

    // ---- Getters ----

    public String getFileName() { return fileName; }
    public String getFileLocation() { return fileLocation; }
    public String getLanguage() { return language; }
    public OcrEngineMode getOcrEngineMode() { return ocrEngineMode; }
    public PageSegmentationMode getPageSegMode() { return pageSegMode; }
    public int getDpi() { return dpi; }
    public String getCharWhitelist() { return charWhitelist; }
    public boolean isPreserveInterwordSpaces() { return preserveInterwordSpaces; }
    public Set<OutputFormat> getOutputFormats() { return outputFormats; }
    public PreprocessingOptions getPreprocessingOptions() { return preprocessingOptions; }

    // ---- Setters for serialization ----

    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileLocation(String fileLocation) { this.fileLocation = fileLocation; }
    public void setLanguage(String language) { this.language = language; }
    public void setOcrEngineMode(OcrEngineMode ocrEngineMode) { this.ocrEngineMode = ocrEngineMode; }
    public void setPageSegMode(PageSegmentationMode pageSegMode) { this.pageSegMode = pageSegMode; }
    public void setDpi(int dpi) { this.dpi = dpi; }
    public void setCharWhitelist(String charWhitelist) { this.charWhitelist = charWhitelist; }
    public void setPreserveInterwordSpaces(boolean preserveInterwordSpaces) { this.preserveInterwordSpaces = preserveInterwordSpaces; }
    public void setOutputFormats(Set<OutputFormat> outputFormats) { this.outputFormats = outputFormats; }
    public void setPreprocessingOptions(PreprocessingOptions preprocessingOptions) { this.preprocessingOptions = preprocessingOptions; }

    @Override
    public String toString() {
        return "OcrRequest{" +
                "fileName='" + fileName + '\'' +
                ", fileLocation='" + fileLocation + '\'' +
                ", language='" + language + '\'' +
                ", oem=" + ocrEngineMode +
                ", psm=" + pageSegMode +
                ", dpi=" + dpi +
                '}';
    }

    // ---- Builder ----

    public static class OcrRequestBuilder {
        private final OcrRequest request = new OcrRequest();

        public OcrRequestBuilder fileName(String val) { request.fileName = val; return this; }
        public OcrRequestBuilder fileLocation(String val) { request.fileLocation = val; return this; }
        public OcrRequestBuilder language(String val) { request.language = val; return this; }
        public OcrRequestBuilder ocrEngineMode(OcrEngineMode val) { request.ocrEngineMode = val; return this; }
        public OcrRequestBuilder pageSegMode(PageSegmentationMode val) { request.pageSegMode = val; return this; }
        public OcrRequestBuilder dpi(int val) { request.dpi = val; return this; }
        public OcrRequestBuilder charWhitelist(String val) { request.charWhitelist = val; return this; }
        public OcrRequestBuilder preserveInterwordSpaces(boolean val) { request.preserveInterwordSpaces = val; return this; }
        public OcrRequestBuilder outputFormats(Set<OutputFormat> val) { request.outputFormats = val; return this; }
        public OcrRequestBuilder preprocessingOptions(PreprocessingOptions val) { request.preprocessingOptions = val; return this; }

        public OcrRequest build() {
            if (request.fileName == null || request.fileName.isBlank()) {
                throw new IllegalArgumentException("fileName is required");
            }
            if (request.fileLocation == null || request.fileLocation.isBlank()) {
                throw new IllegalArgumentException("fileLocation is required");
            }
            return request;
        }
    }
}
