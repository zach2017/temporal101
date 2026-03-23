package com.docprocessing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Value object representing the result returned by the DocumentIntakeWorkflow.
 *
 * <p>Maps to the Python workflow's return dict which is unified across
 * all document categories (PDF, image, office docs, etc.).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentProcessingResult(
        @JsonProperty("document_name") String documentName,
        @JsonProperty("source_mime_type") String sourceMimeType,
        @JsonProperty("category") String category,
        @JsonProperty("text_s3_key") String textS3Key,
        @JsonProperty("image_s3_keys") List<String> imageS3Keys,
        @JsonProperty("ocr_results") List<OcrResult> ocrResults
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OcrResult(
            @JsonProperty("document_name") String documentName,
            @JsonProperty("page_number") int pageNumber,
            @JsonProperty("image_index") int imageIndex,
            @JsonProperty("extracted_text") String extractedText
    ) {}
}
