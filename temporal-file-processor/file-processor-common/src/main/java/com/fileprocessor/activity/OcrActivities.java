package com.fileprocessor.activity;

import com.fileprocessor.model.ExtractedImageInfo;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activities that run Tesseract OCR on image files.
 *
 * <p>Supports JPEG, PNG, TIFF, BMP, GIF, and WebP.  The underlying
 * Tess4J library shells out to the system-installed {@code tesseract}
 * binary, so the host (or Docker container) must have Tesseract and
 * at least the {@code eng} trained-data pack installed.</p>
 */
@ActivityInterface
public interface OcrActivities {

    /**
     * Run OCR on a single image file and write the recognised text
     * to {@code textOutputPath}.
     *
     * @param imagePath      absolute path to the source image
     * @param textOutputPath absolute path where the .txt result should go
     * @return metadata about the extracted image and its OCR text
     */
    @ActivityMethod
    ExtractedImageInfo ocrImage(String imagePath, String textOutputPath);
}
