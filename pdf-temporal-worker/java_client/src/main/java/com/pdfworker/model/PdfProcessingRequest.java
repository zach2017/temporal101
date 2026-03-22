package com.pdfworker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors Python {@code models.PdfProcessingRequest}.
 * Jackson snake_case naming is set globally in the client; the
 * {@code @JsonProperty} annotations are kept as documentation.
 */
public class PdfProcessingRequest {

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("storage_type")
    private String storageType;

    @JsonProperty("location")
    private String location;

    @JsonProperty("extract_images")
    private boolean extractImages;

    public PdfProcessingRequest() {}

    public PdfProcessingRequest(String fileName, StorageType storageType,
                                String location, boolean extractImages) {
        this.fileName      = fileName;
        this.storageType   = storageType.name();
        this.location      = location;
        this.extractImages = extractImages;
    }

    // ── getters / setters ──

    public String  getFileName()      { return fileName; }
    public String  getStorageType()   { return storageType; }
    public String  getLocation()      { return location; }
    public boolean isExtractImages()  { return extractImages; }

    public void setFileName(String v)      { this.fileName = v; }
    public void setStorageType(String v)   { this.storageType = v; }
    public void setLocation(String v)      { this.location = v; }
    public void setExtractImages(boolean v){ this.extractImages = v; }

    @Override
    public String toString() {
        return String.format("PdfProcessingRequest{file=%s, type=%s, loc=%s, imgs=%s}",
                fileName, storageType, location, extractImages);
    }
}
