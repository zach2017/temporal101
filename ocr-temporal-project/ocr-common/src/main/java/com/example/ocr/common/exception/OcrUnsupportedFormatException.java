package com.example.ocr.common.exception;

/**
 * Thrown when the image format is not supported by Tesseract/ImageIO.
 */
public class OcrUnsupportedFormatException extends RuntimeException {

    private final String fileName;

    public OcrUnsupportedFormatException(String fileName, String message) {
        super(message);
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
