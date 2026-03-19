package com.fileprocessor.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

/**
 * Activities for extracting text content from non-image file types.
 *
 * <ul>
 *   <li>Plain text / CSV / JSON / XML → read as-is</li>
 *   <li>PDF → extract text layer via PDFBox</li>
 *   <li>Word / Excel / PowerPoint → extract via Apache POI</li>
 * </ul>
 */
@ActivityInterface
public interface TextExtractionActivities {

    /**
     * Extract the text content of a plain-text file (UTF-8 assumed).
     *
     * @param filePath   source file path
     * @param outputPath destination .txt path
     * @return the output path that was written
     */
    @ActivityMethod
    String extractPlainText(String filePath, String outputPath);

    /**
     * Extract the text layer of a PDF file using Apache PDFBox.
     * <p><b>Note:</b> this does NOT OCR embedded images — that is a
     * separate Activity ({@link OcrActivities#ocrImage}).</p>
     *
     * @param filePath   source PDF path
     * @param outputPath destination .txt path
     * @return the output path that was written
     */
    @ActivityMethod
    String extractPdfText(String filePath, String outputPath);

    /**
     * Extract all embedded images from a PDF and write them to
     * {@code imageOutputDir}.  Returns the list of image file paths.
     *
     * @param filePath       source PDF path
     * @param imageOutputDir directory to write images into
     * @return list of absolute paths of extracted image files
     */
    @ActivityMethod
    List<String> extractPdfImages(String filePath, String imageOutputDir);

    /**
     * Extract text from a Microsoft Office document (docx, xlsx, pptx, etc.)
     * using Apache POI / Tika.
     *
     * @param filePath   source Office file path
     * @param outputPath destination .txt path
     * @return the output path that was written
     */
    @ActivityMethod
    String extractOfficeText(String filePath, String outputPath);
}
