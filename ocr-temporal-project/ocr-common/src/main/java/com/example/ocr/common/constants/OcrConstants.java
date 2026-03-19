package com.example.ocr.common.constants;

/**
 * Shared constants for OCR Temporal workflows.
 */
public final class OcrConstants {

    private OcrConstants() {}

    /** Temporal task queue name for OCR workers. */
    public static final String TASK_QUEUE = "OCR_TASK_QUEUE";

    /** Default language code. */
    public static final String DEFAULT_LANGUAGE = "eng";

    /** Default minimum confidence threshold to consider text as found. */
    public static final float DEFAULT_MIN_CONFIDENCE = 15.0f;

    /** Default target DPI. */
    public static final int DEFAULT_DPI = 300;

    /** Default maximum file size in bytes (50MB). */
    public static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;

    /** Default temporary directory for downloaded files. */
    public static final String DEFAULT_TEMP_DIR = "/tmp/ocr-worker";

    /** Default tessdata path. */
    public static final String DEFAULT_TESSDATA_PATH = "/usr/share/tessdata";

    /** Workflow ID prefix. */
    public static final String WORKFLOW_ID_PREFIX = "ocr-workflow-";
}
