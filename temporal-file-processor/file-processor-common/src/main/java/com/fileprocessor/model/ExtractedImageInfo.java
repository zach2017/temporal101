package com.fileprocessor.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata about a single image extracted from a PDF and OCR'd.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ExtractedImageInfo {

    private final String imageName;
    private final String imagePath;
    private final String ocrTextPath;
    private final long characterCount;

    @JsonCreator
    public ExtractedImageInfo(
            @JsonProperty("imageName")     String imageName,
            @JsonProperty("imagePath")     String imagePath,
            @JsonProperty("ocrTextPath")   String ocrTextPath,
            @JsonProperty("characterCount") long characterCount) {
        this.imageName      = imageName;
        this.imagePath      = imagePath;
        this.ocrTextPath    = ocrTextPath;
        this.characterCount = characterCount;
    }

    public String getImageName()    { return imageName; }
    public String getImagePath()    { return imagePath; }
    public String getOcrTextPath()  { return ocrTextPath; }
    public long getCharacterCount() { return characterCount; }

    @Override
    public String toString() {
        return "ExtractedImageInfo{" +
                "imageName='" + imageName + '\'' +
                ", ocrTextPath='" + ocrTextPath + '\'' +
                ", characterCount=" + characterCount +
                '}';
    }
}
