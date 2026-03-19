package com.example.ocr.common.model;

import java.io.Serializable;

/**
 * Configuration for image preprocessing steps applied before OCR.
 * All steps are optional and configurable.
 */
public class PreprocessingOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean convertToGrayscale = true;
    private boolean removeNoise = false;
    private boolean addBorder = true;
    private int borderSizePx = 10;
    private boolean removeAlpha = true;
    private boolean autoRotate = false;

    public PreprocessingOptions() {
    }

    // ---- Builder-style setters ----

    public PreprocessingOptions convertToGrayscale(boolean val) {
        this.convertToGrayscale = val;
        return this;
    }

    public PreprocessingOptions removeNoise(boolean val) {
        this.removeNoise = val;
        return this;
    }

    public PreprocessingOptions addBorder(boolean val) {
        this.addBorder = val;
        return this;
    }

    public PreprocessingOptions borderSizePx(int val) {
        this.borderSizePx = val;
        return this;
    }

    public PreprocessingOptions removeAlpha(boolean val) {
        this.removeAlpha = val;
        return this;
    }

    public PreprocessingOptions autoRotate(boolean val) {
        this.autoRotate = val;
        return this;
    }

    // ---- Getters ----

    public boolean isConvertToGrayscale() { return convertToGrayscale; }
    public boolean isRemoveNoise() { return removeNoise; }
    public boolean isAddBorder() { return addBorder; }
    public int getBorderSizePx() { return borderSizePx; }
    public boolean isRemoveAlpha() { return removeAlpha; }
    public boolean isAutoRotate() { return autoRotate; }

    // ---- Standard setters for serialization ----

    public void setConvertToGrayscale(boolean convertToGrayscale) { this.convertToGrayscale = convertToGrayscale; }
    public void setRemoveNoise(boolean removeNoise) { this.removeNoise = removeNoise; }
    public void setAddBorder(boolean addBorder) { this.addBorder = addBorder; }
    public void setBorderSizePx(int borderSizePx) { this.borderSizePx = borderSizePx; }
    public void setRemoveAlpha(boolean removeAlpha) { this.removeAlpha = removeAlpha; }
    public void setAutoRotate(boolean autoRotate) { this.autoRotate = autoRotate; }

    /** Returns a default configuration with sensible defaults. */
    public static PreprocessingOptions defaults() {
        return new PreprocessingOptions();
    }

    @Override
    public String toString() {
        return "PreprocessingOptions{" +
                "grayscale=" + convertToGrayscale +
                ", noise=" + removeNoise +
                ", border=" + addBorder + "(" + borderSizePx + "px)" +
                ", alpha=" + removeAlpha +
                ", rotate=" + autoRotate +
                '}';
    }
}
