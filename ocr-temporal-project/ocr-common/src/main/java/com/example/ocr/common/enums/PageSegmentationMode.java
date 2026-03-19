package com.example.ocr.common.enums;

/**
 * Tesseract Page Segmentation Modes (PSM).
 * Controls how Tesseract segments the image before recognition.
 */
public enum PageSegmentationMode {

    OSD_ONLY(0, "Orientation and script detection only"),
    AUTO_OSD(1, "Automatic page segmentation with OSD"),
    AUTO_NO_OSD(2, "Automatic page segmentation, no OSD or OCR"),
    FULLY_AUTOMATIC(3, "Fully automatic page segmentation, no OSD (Default)"),
    SINGLE_COLUMN(4, "Single column of text of variable sizes"),
    VERTICAL_BLOCK(5, "Single uniform block of vertically aligned text"),
    UNIFORM_BLOCK(6, "Single uniform block of text"),
    SINGLE_LINE(7, "Treat image as a single text line"),
    SINGLE_WORD(8, "Treat image as a single word"),
    CIRCLE_WORD(9, "Treat image as a single word in a circle"),
    SINGLE_CHAR(10, "Treat image as a single character"),
    SPARSE_TEXT(11, "Find as much text as possible in no particular order"),
    SPARSE_OSD(12, "Sparse text with OSD"),
    RAW_LINE(13, "Raw line, bypassing Tesseract-specific hacks");

    private final int value;
    private final String description;

    PageSegmentationMode(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
