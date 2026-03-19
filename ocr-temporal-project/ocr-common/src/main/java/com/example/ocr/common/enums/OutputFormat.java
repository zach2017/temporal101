package com.example.ocr.common.enums;

/**
 * Supported OCR output formats.
 */
public enum OutputFormat {
    /** Plain text extraction */
    PLAIN_TEXT,
    /** HOCR XML with word bounding boxes */
    HOCR,
    /** Tab-separated values with positional data */
    TSV
}
