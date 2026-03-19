package com.example.ocr.common.exception;

/**
 * Thrown when the source file cannot be found or accessed.
 */
public class OcrFileNotFoundException extends RuntimeException {

    private final String fileLocation;

    public OcrFileNotFoundException(String fileLocation, String message) {
        super(message);
        this.fileLocation = fileLocation;
    }

    public OcrFileNotFoundException(String fileLocation, String message, Throwable cause) {
        super(message, cause);
        this.fileLocation = fileLocation;
    }

    public String getFileLocation() {
        return fileLocation;
    }
}
