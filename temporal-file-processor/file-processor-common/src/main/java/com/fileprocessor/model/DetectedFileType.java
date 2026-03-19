package com.fileprocessor.model;

/**
 * High-level categorisation of a file based on its detected MIME type.
 * The Worker uses this to route each file to the correct extraction strategy.
 */
public enum DetectedFileType {

    /** JPEG, PNG, TIFF, BMP, GIF, WebP — sent to Tesseract OCR. */
    IMAGE,

    /** application/pdf — text is extracted directly AND images are sent to OCR. */
    PDF,

    /** MS Word (.doc / .docx), RTF — text extracted via Apache POI / Tika. */
    WORD_DOCUMENT,

    /** MS Excel (.xls / .xlsx) — cell text extracted via Apache POI. */
    SPREADSHEET,

    /** MS PowerPoint (.ppt / .pptx) — slide text extracted via Apache POI. */
    PRESENTATION,

    /** text/plain, text/csv, text/html, text/xml, application/json, etc. */
    PLAIN_TEXT,

    /** Anything we cannot (or choose not to) extract text from. */
    UNSUPPORTED
}
