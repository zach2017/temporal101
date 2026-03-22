package com.pdfworker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Mirrors Python {@code models.PdfProcessingResult}.
 */
public class PdfProcessingResult {

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("storage_type")
    private String storageType;

    @JsonProperty("text_storage_path")
    private String textStoragePath;

    @JsonProperty("page_count")
    private int pageCount;

    @JsonProperty("image_count")
    private int imageCount;

    @JsonProperty("text_content")
    private String textContent;

    @JsonProperty("images")
    private List<ExtractedImage> images;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("error_message")
    private String errorMessage;

    public PdfProcessingResult() {}

    // ── getters ──

    public String  getFileName()        { return fileName; }
    public String  getStorageType()     { return storageType; }
    public String  getTextStoragePath() { return textStoragePath; }
    public int     getPageCount()       { return pageCount; }
    public int     getImageCount()      { return imageCount; }
    public String  getTextContent()     { return textContent; }
    public List<ExtractedImage> getImages() { return images; }
    public boolean isSuccess()          { return success; }
    public String  getErrorMessage()    { return errorMessage; }

    // ── setters ──

    public void setFileName(String v)        { this.fileName = v; }
    public void setStorageType(String v)     { this.storageType = v; }
    public void setTextStoragePath(String v) { this.textStoragePath = v; }
    public void setPageCount(int v)          { this.pageCount = v; }
    public void setImageCount(int v)         { this.imageCount = v; }
    public void setTextContent(String v)     { this.textContent = v; }
    public void setImages(List<ExtractedImage> v) { this.images = v; }
    public void setSuccess(boolean v)        { this.success = v; }
    public void setErrorMessage(String v)    { this.errorMessage = v; }

    /** Pretty-print for CLI output. */
    public String toReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════╗\n");
        sb.append("║           PDF Processing Result                 ║\n");
        sb.append("╠══════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ File     : %-37s ║%n", fileName));
        sb.append(String.format("║ Storage  : %-37s ║%n", storageType));
        sb.append(String.format("║ Pages    : %-37d ║%n", pageCount));
        sb.append(String.format("║ Images   : %-37d ║%n", imageCount));
        sb.append(String.format("║ Success  : %-37s ║%n", success));
        sb.append("╠══════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Text at  : %s%n", textStoragePath));
        if (images != null && !images.isEmpty()) {
            sb.append("║ Images:").append("\n");
            for (ExtractedImage img : images) {
                sb.append("║   ").append(img).append("\n");
            }
        }
        if (errorMessage != null && !errorMessage.isEmpty()) {
            sb.append(String.format("║ ERROR    : %s%n", errorMessage));
        }
        sb.append("╚══════════════════════════════════════════════════╝");
        return sb.toString();
    }
}
