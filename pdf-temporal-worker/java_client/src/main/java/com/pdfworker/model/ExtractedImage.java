package com.pdfworker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors Python {@code models.ExtractedImage}.
 */
public class ExtractedImage {

    @JsonProperty("image_name")
    private String imageName;

    @JsonProperty("page_number")
    private int pageNumber;

    @JsonProperty("storage_path")
    private String storagePath;

    public ExtractedImage() {}

    public String getImageName()   { return imageName; }
    public int    getPageNumber()  { return pageNumber; }
    public String getStoragePath() { return storagePath; }

    public void setImageName(String v)   { this.imageName = v; }
    public void setPageNumber(int v)     { this.pageNumber = v; }
    public void setStoragePath(String v) { this.storagePath = v; }

    @Override
    public String toString() {
        return String.format("  p%d  %-30s → %s", pageNumber, imageName, storagePath);
    }
}
