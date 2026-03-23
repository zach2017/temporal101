package com.pdfextraction.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Value object representing the result returned by the PDF extraction workflow.
 * <p>
 * Maps directly to the Python workflow's return dict.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfExtractionResult(
        @JsonProperty("document_name") String documentName,
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
