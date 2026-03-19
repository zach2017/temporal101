package com.fileprocessor.worker.util;

import com.fileprocessor.model.DetectedFileType;

import java.util.Map;
import java.util.Set;

/**
 * Maps raw MIME-type strings to {@link DetectedFileType} categories.
 */
public final class MimeTypeResolver {

    private static final Set<String> IMAGE_MIMES = Set.of(
            "image/jpeg", "image/png", "image/tiff", "image/bmp",
            "image/gif", "image/webp", "image/x-ms-bmp");

    private static final Set<String> TEXT_MIMES = Set.of(
            "text/plain", "text/csv", "text/html", "text/xml",
            "text/markdown", "application/json", "application/xml",
            "application/x-yaml", "text/x-java-source",
            "text/x-python", "text/x-script.python",
            "application/javascript", "text/css");

    private static final Map<String, DetectedFileType> EXACT_MAP = Map.ofEntries(
            // PDF
            Map.entry("application/pdf", DetectedFileType.PDF),
            // Word
            Map.entry("application/msword", DetectedFileType.WORD_DOCUMENT),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    DetectedFileType.WORD_DOCUMENT),
            Map.entry("application/rtf", DetectedFileType.WORD_DOCUMENT),
            // Excel
            Map.entry("application/vnd.ms-excel", DetectedFileType.SPREADSHEET),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    DetectedFileType.SPREADSHEET),
            // PowerPoint
            Map.entry("application/vnd.ms-powerpoint", DetectedFileType.PRESENTATION),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    DetectedFileType.PRESENTATION)
    );

    private MimeTypeResolver() { /* utility class */ }

    /**
     * Resolve a MIME-type string to a {@link DetectedFileType}.
     *
     * @param mimeType the detected MIME string (e.g. "application/pdf")
     * @return the high-level category, or {@link DetectedFileType#UNSUPPORTED}
     */
    public static DetectedFileType resolve(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return DetectedFileType.UNSUPPORTED;
        }

        String normalized = mimeType.strip().toLowerCase();

        // Check exact matches first
        DetectedFileType exact = EXACT_MAP.get(normalized);
        if (exact != null) return exact;

        // Image family
        if (IMAGE_MIMES.contains(normalized) || normalized.startsWith("image/")) {
            return DetectedFileType.IMAGE;
        }

        // Text family
        if (TEXT_MIMES.contains(normalized) || normalized.startsWith("text/")) {
            return DetectedFileType.PLAIN_TEXT;
        }

        return DetectedFileType.UNSUPPORTED;
    }
}
