package com.example.ocr.common.exception;

/**
 * Thrown when the Tesseract OCR engine encounters an error during processing.
 */
public class OcrProcessingException extends RuntimeException {

    public OcrProcessingException(String message) {
        super(message);
    }

    public OcrProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
