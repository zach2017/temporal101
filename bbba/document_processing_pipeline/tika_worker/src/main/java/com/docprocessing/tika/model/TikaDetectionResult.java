package com.docprocessing.tika.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Output payload from the {@code detect_file_type_tika} activity.
 *
 * <p>The {@code category} field maps to the Python
 * {@code DocumentCategory} enum values so the intake workflow
 * can route correctly without any Python-side MIME detection.
 */
public record TikaDetectionResult(
        @JsonProperty("file_name") String fileName,
        @JsonProperty("file_location") String fileLocation,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("category") String category,
        @JsonProperty("encoding") String encoding
) {

    /**
     * Map a MIME type to a category string matching the Python
     * {@code DocumentCategory} enum.
     */
    public static String categorise(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) return "unknown";

        // PDF
        if (mimeType.equals("application/pdf")) return "pdf";

        // Images
        if (mimeType.startsWith("image/")) return "image";

        // Office documents
        if (mimeType.contains("wordprocessingml") || mimeType.equals("application/msword")
                || mimeType.contains("opendocument.text")) return "office_document";
        if (mimeType.contains("spreadsheetml") || mimeType.equals("application/vnd.ms-excel")
                || mimeType.contains("opendocument.spreadsheet")) return "office_document";
        if (mimeType.contains("presentationml") || mimeType.equals("application/vnd.ms-powerpoint")
                || mimeType.contains("opendocument.presentation")) return "office_document";

        // Rich text
        if (mimeType.equals("text/rtf") || mimeType.equals("application/rtf")) return "rich_text";

        // HTML
        if (mimeType.equals("text/html") || mimeType.equals("application/xhtml+xml")
                || mimeType.contains("xml")) return "html";

        // Ebook
        if (mimeType.equals("application/epub+zip")) return "ebook";

        // Email
        if (mimeType.equals("message/rfc822") || mimeType.contains("ms-outlook")) return "email";

        // Plain text (catch-all for text/*)
        if (mimeType.startsWith("text/")) return "plain_text";

        return "unknown";
    }
}
