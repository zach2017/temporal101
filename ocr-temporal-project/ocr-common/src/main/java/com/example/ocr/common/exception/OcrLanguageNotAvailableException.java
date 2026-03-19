package com.example.ocr.common.exception;

import java.util.List;

/**
 * Thrown when one or more requested language traineddata files are not installed.
 */
public class OcrLanguageNotAvailableException extends RuntimeException {

    private final List<String> missingLanguages;

    public OcrLanguageNotAvailableException(List<String> missingLanguages) {
        super("Missing language traineddata files: " + String.join(", ", missingLanguages));
        this.missingLanguages = missingLanguages;
    }

    public List<String> getMissingLanguages() {
        return missingLanguages;
    }
}
