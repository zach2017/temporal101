package com.example.ocr.common.enums;

/**
 * Tesseract OCR Engine Modes.
 * Controls which recognition engine Tesseract uses.
 */
public enum OcrEngineMode {

    /** Original Tesseract engine (pattern matching). Requires tessdata repo models. */
    LEGACY(0),

    /** LSTM neural network engine. Default and recommended. */
    LSTM_ONLY(1),

    /** Combined Legacy + LSTM engine. Requires tessdata repo models. */
    LEGACY_LSTM(2),

    /** Use whatever is available in the trained data file. */
    DEFAULT(3);

    private final int value;

    OcrEngineMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
