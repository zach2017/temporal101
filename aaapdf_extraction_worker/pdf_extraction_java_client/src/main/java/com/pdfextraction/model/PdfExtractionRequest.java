package com.pdfextraction.model;

/**
 * Value object representing the input to the PDF extraction workflow.
 *
 * @param fileName     the PDF file name (e.g. "invoice_2024.pdf")
 * @param fileLocation absolute path or URI to the source file
 */
public record PdfExtractionRequest(
        String fileName,
        String fileLocation
) {

    public PdfExtractionRequest {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (fileLocation == null || fileLocation.isBlank()) {
            throw new IllegalArgumentException("fileLocation must not be blank");
        }
    }
}
